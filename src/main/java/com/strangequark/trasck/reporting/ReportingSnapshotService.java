package com.strangequark.trasck.reporting;

import java.time.LocalDate;
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

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ReportingSnapshotService(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
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
