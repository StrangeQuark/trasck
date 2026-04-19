package com.strangequark.trasck.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingSnapshotService {

    static final int DEFAULT_RAW_RETENTION_DAYS = 730;
    static final int DEFAULT_WEEKLY_ROLLUP_AFTER_DAYS = 90;
    static final int DEFAULT_MONTHLY_ROLLUP_AFTER_DAYS = 365;
    static final int DEFAULT_ARCHIVE_AFTER_DAYS = 1825;

    private static final UUID NO_TEAM_SCOPE = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ReportingSnapshotArchiveRunRepository archiveRunRepository;
    private final ObjectMapper objectMapper;

    public ReportingSnapshotService(
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ReportingSnapshotArchiveRunRepository archiveRunRepository,
            ObjectMapper objectMapper
    ) {
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.archiveRunRepository = archiveRunRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${trasck.reporting.snapshots.cron:0 15 2 * * *}", zone = "UTC")
    @Transactional
    public void runScheduledSnapshots() {
        LocalDate snapshotDate = LocalDate.now(ZoneOffset.UTC);
        List<UUID> workspaceIds = namedJdbcTemplate.queryForList("""
                select id
                from workspaces
                where deleted_at is null
                  and status = 'active'
                """, new MapSqlParameterSource(), UUID.class);
        for (UUID workspaceId : workspaceIds) {
            runWorkspaceSnapshots(workspaceId, snapshotDate);
        }
    }

    @Transactional
    public ReportingSnapshotRunResponse runWorkspaceSnapshots(UUID workspaceId, LocalDate snapshotDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("snapshotDate", snapshotDate);
        int cycleTimeRecords = snapshotCycleTimeRecords(params);
        int iterationSnapshots = snapshotIterations(params);
        int velocitySnapshots = snapshotVelocity(params);
        int cumulativeFlowSnapshots = snapshotCumulativeFlow(params);
        return new ReportingSnapshotRunResponse(
                workspaceId,
                snapshotDate,
                cycleTimeRecords,
                iterationSnapshots,
                velocitySnapshots,
                cumulativeFlowSnapshots
        );
    }

    @Transactional
    public ReportingRollupRunResponse runWorkspaceRollups(
            UUID workspaceId,
            ReportingRetentionPolicy policy,
            UUID actorId,
            LocalDate fromDate,
            LocalDate toDate,
            String granularity,
            String action
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("granularity", granularity)
                .addValue("noTeamScope", NO_TEAM_SCOPE);
        int cycleTimeRollups = rollupCycleTime(params, granularity);
        int iterationRollups = rollupIterations(params, granularity);
        int velocityRollups = rollupVelocity(params, granularity);
        int cumulativeFlowRollups = rollupCumulativeFlow(params, granularity);
        ReportingSnapshotArchiveRun archiveRun = new ReportingSnapshotArchiveRun();
        archiveRun.setWorkspaceId(workspaceId);
        archiveRun.setRetentionPolicyId(policy == null ? null : policy.getId());
        archiveRun.setRequestedById(actorId);
        archiveRun.setAction(action);
        archiveRun.setGranularity(granularity);
        archiveRun.setFromDate(fromDate);
        archiveRun.setToDate(toDate);
        archiveRun.setPolicySnapshot(policySnapshot(policy, workspaceId));
        archiveRun.setCycleTimeRollups(cycleTimeRollups);
        archiveRun.setIterationRollups(iterationRollups);
        archiveRun.setVelocityRollups(velocityRollups);
        archiveRun.setCumulativeFlowRollups(cumulativeFlowRollups);
        archiveRun.setGenericRollups(0);
        archiveRun.setRawRowsPruned(0);
        archiveRun.setStatus("completed");
        archiveRun.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ReportingSnapshotArchiveRun saved = archiveRunRepository.save(archiveRun);
        return new ReportingRollupRunResponse(
                workspaceId,
                saved.getId(),
                action,
                granularity,
                fromDate,
                toDate,
                cycleTimeRollups,
                iterationRollups,
                velocityRollups,
                cumulativeFlowRollups,
                0,
                0
        );
    }

    private int rollupCycleTime(MapSqlParameterSource params, String granularity) {
        String bucketStart = bucketStartExpression("ctr.completed_at::date", granularity);
        return namedJdbcTemplate.update("""
                insert into reporting_cycle_time_rollups (
                    workspace_id,
                    project_id,
                    granularity,
                    bucket_start_date,
                    bucket_end_date,
                    work_item_count,
                    lead_time_minutes_sum,
                    cycle_time_minutes_sum,
                    lead_time_minutes_avg,
                    cycle_time_minutes_avg
                )
                with bucketed as (
                    select wi.workspace_id,
                           wi.project_id,
                           %s as bucket_start_date,
                           ctr.lead_time_minutes,
                           ctr.cycle_time_minutes
                    from cycle_time_records ctr
                    join work_items wi on wi.id = ctr.work_item_id
                    where wi.workspace_id = :workspaceId
                      and wi.deleted_at is null
                      and ctr.completed_at is not null
                      and ctr.completed_at::date >= :fromDate
                      and ctr.completed_at::date <= :toDate
                )
                select workspace_id,
                       project_id,
                       :granularity,
                       bucket_start_date,
                       reporting_bucket_end(bucket_start_date, :granularity),
                       count(*)::integer,
                       coalesce(sum(lead_time_minutes), 0)::bigint,
                       coalesce(sum(cycle_time_minutes), 0)::bigint,
                       coalesce(round(avg(lead_time_minutes)::numeric, 2), 0),
                       coalesce(round(avg(cycle_time_minutes)::numeric, 2), 0)
                from bucketed
                group by workspace_id, project_id, bucket_start_date
                on conflict (project_id, granularity, bucket_start_date) do update set
                    bucket_end_date = excluded.bucket_end_date,
                    work_item_count = excluded.work_item_count,
                    lead_time_minutes_sum = excluded.lead_time_minutes_sum,
                    cycle_time_minutes_sum = excluded.cycle_time_minutes_sum,
                    lead_time_minutes_avg = excluded.lead_time_minutes_avg,
                    cycle_time_minutes_avg = excluded.cycle_time_minutes_avg,
                    updated_at = now()
                """.formatted(bucketStart), params);
    }

    private int rollupIterations(MapSqlParameterSource params, String granularity) {
        String bucketStart = bucketStartExpression("snapshot.snapshot_date", granularity);
        return namedJdbcTemplate.update("""
                insert into reporting_iteration_rollups (
                    workspace_id,
                    project_id,
                    team_id,
                    team_scope_key,
                    granularity,
                    bucket_start_date,
                    bucket_end_date,
                    iteration_count,
                    committed_points,
                    completed_points,
                    remaining_points,
                    scope_added_points,
                    scope_removed_points
                )
                with bucketed as (
                    select iteration.workspace_id,
                           iteration.project_id,
                           iteration.team_id,
                           coalesce(iteration.team_id, :noTeamScope) as team_scope_key,
                           %s as bucket_start_date,
                           snapshot.iteration_id,
                           snapshot.committed_points,
                           snapshot.completed_points,
                           snapshot.remaining_points,
                           snapshot.scope_added_points,
                           snapshot.scope_removed_points
                    from iteration_snapshots snapshot
                    join iterations iteration on iteration.id = snapshot.iteration_id
                    where iteration.workspace_id = :workspaceId
                      and iteration.project_id is not null
                      and snapshot.snapshot_date >= :fromDate
                      and snapshot.snapshot_date <= :toDate
                )
                select workspace_id,
                       project_id,
                       team_id,
                       team_scope_key,
                       :granularity,
                       bucket_start_date,
                       reporting_bucket_end(bucket_start_date, :granularity),
                       count(distinct iteration_id)::integer,
                       coalesce(sum(committed_points), 0),
                       coalesce(sum(completed_points), 0),
                       coalesce(sum(remaining_points), 0),
                       coalesce(sum(scope_added_points), 0),
                       coalesce(sum(scope_removed_points), 0)
                from bucketed
                group by workspace_id, project_id, team_id, team_scope_key, bucket_start_date
                on conflict (project_id, team_scope_key, granularity, bucket_start_date) do update set
                    team_id = excluded.team_id,
                    bucket_end_date = excluded.bucket_end_date,
                    iteration_count = excluded.iteration_count,
                    committed_points = excluded.committed_points,
                    completed_points = excluded.completed_points,
                    remaining_points = excluded.remaining_points,
                    scope_added_points = excluded.scope_added_points,
                    scope_removed_points = excluded.scope_removed_points,
                    updated_at = now()
                """.formatted(bucketStart), params);
    }

    private int rollupVelocity(MapSqlParameterSource params, String granularity) {
        String sourceDate = "coalesce(iteration.end_date, iteration.start_date)";
        String bucketStart = bucketStartExpression(sourceDate, granularity);
        return namedJdbcTemplate.update("""
                insert into reporting_velocity_rollups (
                    workspace_id,
                    project_id,
                    team_id,
                    granularity,
                    bucket_start_date,
                    bucket_end_date,
                    iteration_count,
                    committed_points,
                    completed_points,
                    carried_over_points
                )
                with bucketed as (
                    select iteration.workspace_id,
                           iteration.project_id,
                           snapshot.team_id,
                           %s as bucket_start_date,
                           snapshot.iteration_id,
                           snapshot.committed_points,
                           snapshot.completed_points,
                           snapshot.carried_over_points
                    from velocity_snapshots snapshot
                    join iterations iteration on iteration.id = snapshot.iteration_id
                    where iteration.workspace_id = :workspaceId
                      and iteration.project_id is not null
                      and coalesce(iteration.end_date, iteration.start_date) is not null
                      and coalesce(iteration.end_date, iteration.start_date) >= :fromDate
                      and coalesce(iteration.end_date, iteration.start_date) <= :toDate
                )
                select workspace_id,
                       project_id,
                       team_id,
                       :granularity,
                       bucket_start_date,
                       reporting_bucket_end(bucket_start_date, :granularity),
                       count(distinct iteration_id)::integer,
                       coalesce(sum(committed_points), 0),
                       coalesce(sum(completed_points), 0),
                       coalesce(sum(carried_over_points), 0)
                from bucketed
                group by workspace_id, project_id, team_id, bucket_start_date
                on conflict (project_id, team_id, granularity, bucket_start_date) do update set
                    bucket_end_date = excluded.bucket_end_date,
                    iteration_count = excluded.iteration_count,
                    committed_points = excluded.committed_points,
                    completed_points = excluded.completed_points,
                    carried_over_points = excluded.carried_over_points,
                    updated_at = now()
                """.formatted(bucketStart), params);
    }

    private int rollupCumulativeFlow(MapSqlParameterSource params, String granularity) {
        String bucketStart = bucketStartExpression("snapshot.snapshot_date", granularity);
        return namedJdbcTemplate.update("""
                insert into reporting_cumulative_flow_rollups (
                    workspace_id,
                    project_id,
                    board_id,
                    status_id,
                    granularity,
                    bucket_start_date,
                    bucket_end_date,
                    snapshot_count,
                    work_item_count_avg,
                    work_item_count_max,
                    total_points_avg,
                    total_points_max
                )
                with bucketed as (
                    select board.workspace_id,
                           board.project_id,
                           snapshot.board_id,
                           snapshot.status_id,
                           %s as bucket_start_date,
                           snapshot.work_item_count,
                           snapshot.total_points
                    from cumulative_flow_snapshots snapshot
                    join boards board on board.id = snapshot.board_id
                    where board.workspace_id = :workspaceId
                      and board.project_id is not null
                      and snapshot.snapshot_date >= :fromDate
                      and snapshot.snapshot_date <= :toDate
                )
                select workspace_id,
                       project_id,
                       board_id,
                       status_id,
                       :granularity,
                       bucket_start_date,
                       reporting_bucket_end(bucket_start_date, :granularity),
                       count(*)::integer,
                       coalesce(round(avg(work_item_count)::numeric, 2), 0),
                       coalesce(max(work_item_count), 0)::integer,
                       coalesce(round(avg(total_points)::numeric, 2), 0),
                       coalesce(max(total_points), 0)
                from bucketed
                group by workspace_id, project_id, board_id, status_id, bucket_start_date
                on conflict (board_id, status_id, granularity, bucket_start_date) do update set
                    bucket_end_date = excluded.bucket_end_date,
                    snapshot_count = excluded.snapshot_count,
                    work_item_count_avg = excluded.work_item_count_avg,
                    work_item_count_max = excluded.work_item_count_max,
                    total_points_avg = excluded.total_points_avg,
                    total_points_max = excluded.total_points_max,
                    updated_at = now()
                """.formatted(bucketStart), params);
    }

    private String bucketStartExpression(String dateExpression, String granularity) {
        return switch (granularity) {
            case "daily" -> dateExpression;
            case "weekly" -> "date_trunc('week', " + dateExpression + "::timestamp)::date";
            case "monthly" -> "date_trunc('month', " + dateExpression + "::timestamp)::date";
            default -> throw new IllegalArgumentException("Unsupported rollup granularity");
        };
    }

    private ObjectNode policySnapshot(ReportingRetentionPolicy policy, UUID workspaceId) {
        ObjectNode snapshot = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("rawRetentionDays", policy == null || policy.getRawRetentionDays() == null ? DEFAULT_RAW_RETENTION_DAYS : policy.getRawRetentionDays())
                .put("weeklyRollupAfterDays", policy == null || policy.getWeeklyRollupAfterDays() == null ? DEFAULT_WEEKLY_ROLLUP_AFTER_DAYS : policy.getWeeklyRollupAfterDays())
                .put("monthlyRollupAfterDays", policy == null || policy.getMonthlyRollupAfterDays() == null ? DEFAULT_MONTHLY_ROLLUP_AFTER_DAYS : policy.getMonthlyRollupAfterDays())
                .put("archiveAfterDays", policy == null || policy.getArchiveAfterDays() == null ? DEFAULT_ARCHIVE_AFTER_DAYS : policy.getArchiveAfterDays())
                .put("destructivePruningEnabled", policy != null && Boolean.TRUE.equals(policy.getDestructivePruningEnabled()));
        if (policy != null && policy.getId() != null) {
            snapshot.put("policyId", policy.getId().toString());
        }
        return snapshot;
    }

    private int snapshotCycleTimeRecords(MapSqlParameterSource params) {
        return namedJdbcTemplate.update("""
                insert into cycle_time_records (
                    work_item_id,
                    created_at,
                    started_at,
                    completed_at,
                    lead_time_minutes,
                    cycle_time_minutes
                )
                with timing as (
                    select wi.id as work_item_id,
                           wi.created_at,
                           (
                               select min(history.changed_at)
                               from work_item_status_history history
                               join workflow_statuses status on status.id = history.to_status_id
                               where history.work_item_id = wi.id
                                 and status.category = 'in_progress'
                           ) as started_at,
                           coalesce(
                               wi.resolved_at,
                               (
                                   select min(history.changed_at)
                                   from work_item_status_history history
                                   join workflow_statuses status on status.id = history.to_status_id
                                   where history.work_item_id = wi.id
                                     and status.terminal = true
                               )
                           ) as completed_at
                    from work_items wi
                    join workflow_statuses current_status on current_status.id = wi.status_id
                    where wi.workspace_id = :workspaceId
                      and wi.deleted_at is null
                      and (
                          wi.resolved_at is not null
                          or current_status.terminal = true
                          or exists (
                              select 1
                              from work_item_status_history history
                              join workflow_statuses status on status.id = history.to_status_id
                              where history.work_item_id = wi.id
                                and status.terminal = true
                          )
                      )
                )
                select work_item_id,
                       created_at,
                       started_at,
                       completed_at,
                       greatest(0, round(extract(epoch from (completed_at - created_at)) / 60.0)::integer) as lead_time_minutes,
                       case
                           when started_at is null then null
                           else greatest(0, round(extract(epoch from (completed_at - started_at)) / 60.0)::integer)
                       end as cycle_time_minutes
                from timing
                where completed_at is not null
                on conflict (work_item_id) do update set
                    created_at = excluded.created_at,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at,
                    lead_time_minutes = excluded.lead_time_minutes,
                    cycle_time_minutes = excluded.cycle_time_minutes
                """, params);
    }

    private int snapshotIterations(MapSqlParameterSource params) {
        return namedJdbcTemplate.update("""
                insert into iteration_snapshots (
                    iteration_id,
                    snapshot_date,
                    committed_points,
                    completed_points,
                    remaining_points,
                    scope_added_points,
                    scope_removed_points
                )
                select iteration.id,
                       :snapshotDate,
                       coalesce(
                           iteration.committed_points,
                           coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (where iteration_work_item.removed_at is null), 0)
                       ) as committed_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at is null
                             and coalesce(status.terminal, false) = true
                       ), 0) as completed_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at is null
                             and coalesce(status.terminal, false) = false
                       ), 0) as remaining_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.added_at::date = :snapshotDate
                       ), 0) as scope_added_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at::date = :snapshotDate
                       ), 0) as scope_removed_points
                from iterations iteration
                left join iteration_work_items iteration_work_item on iteration_work_item.iteration_id = iteration.id
                left join work_items work_item on work_item.id = iteration_work_item.work_item_id
                    and work_item.deleted_at is null
                left join workflow_statuses status on status.id = work_item.status_id
                where iteration.workspace_id = :workspaceId
                group by iteration.id, iteration.committed_points
                on conflict (iteration_id, snapshot_date) do update set
                    committed_points = excluded.committed_points,
                    completed_points = excluded.completed_points,
                    remaining_points = excluded.remaining_points,
                    scope_added_points = excluded.scope_added_points,
                    scope_removed_points = excluded.scope_removed_points
                """, params);
    }

    private int snapshotVelocity(MapSqlParameterSource params) {
        return namedJdbcTemplate.update("""
                insert into velocity_snapshots (
                    team_id,
                    iteration_id,
                    committed_points,
                    completed_points,
                    carried_over_points
                )
                select iteration.team_id,
                       iteration.id,
                       coalesce(
                           iteration_snapshot.committed_points,
                           iteration.committed_points,
                           coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (where iteration_work_item.removed_at is null), 0)
                       ) as committed_points,
                       coalesce(
                           iteration_snapshot.completed_points,
                           coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                               where iteration_work_item.removed_at is null
                                 and coalesce(status.terminal, false) = true
                           ), 0)
                       ) as completed_points,
                       coalesce(
                           iteration_snapshot.remaining_points,
                           coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                               where iteration_work_item.removed_at is null
                                 and coalesce(status.terminal, false) = false
                           ), 0)
                       ) as carried_over_points
                from iterations iteration
                left join iteration_snapshots iteration_snapshot on iteration_snapshot.iteration_id = iteration.id
                    and iteration_snapshot.snapshot_date = :snapshotDate
                left join iteration_work_items iteration_work_item on iteration_work_item.iteration_id = iteration.id
                left join work_items work_item on work_item.id = iteration_work_item.work_item_id
                    and work_item.deleted_at is null
                left join workflow_statuses status on status.id = work_item.status_id
                where iteration.workspace_id = :workspaceId
                  and iteration.team_id is not null
                group by iteration.id,
                         iteration.team_id,
                         iteration.committed_points,
                         iteration_snapshot.committed_points,
                         iteration_snapshot.completed_points,
                         iteration_snapshot.remaining_points
                on conflict (team_id, iteration_id) do update set
                    committed_points = excluded.committed_points,
                    completed_points = excluded.completed_points,
                    carried_over_points = excluded.carried_over_points
                """, params);
    }

    private int snapshotCumulativeFlow(MapSqlParameterSource params) {
        return namedJdbcTemplate.update("""
                insert into cumulative_flow_snapshots (
                    board_id,
                    snapshot_date,
                    status_id,
                    work_item_count,
                    total_points
                )
                select board.id,
                       :snapshotDate,
                       status.id,
                       count(work_item.id)::integer as work_item_count,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)), 0) as total_points
                from boards board
                join board_columns board_column on board_column.board_id = board.id
                join lateral jsonb_array_elements_text(board_column.status_ids) status_json(status_id) on true
                join workflow_statuses status on status.id = status_json.status_id::uuid
                left join work_items work_item on work_item.workspace_id = board.workspace_id
                    and work_item.status_id = status.id
                    and work_item.deleted_at is null
                    and (board.project_id is null or work_item.project_id = board.project_id)
                    and (board.team_id is null or work_item.team_id = board.team_id)
                where board.workspace_id = :workspaceId
                  and board.active = true
                group by board.id, status.id
                on conflict (board_id, snapshot_date, status_id) do update set
                    work_item_count = excluded.work_item_count,
                    total_points = excluded.total_points
                """, params);
    }
}
