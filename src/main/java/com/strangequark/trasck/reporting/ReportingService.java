package com.strangequark.trasck.reporting;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.activity.WorkLog;
import com.strangequark.trasck.activity.WorkLogRepository;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.planning.Iteration;
import com.strangequark.trasck.planning.IterationRepository;
import com.strangequark.trasck.project.Program;
import com.strangequark.trasck.project.ProgramProject;
import com.strangequark.trasck.project.ProgramProjectRepository;
import com.strangequark.trasck.project.ProgramRepository;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.team.ProjectTeamRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportingService {

    private static final int DEFAULT_REPORT_DAYS = 30;
    private static final int STALE_OPEN_WORK_DAYS = 14;
    private static final String TEAM_RESOLUTION_MODE = "explicit_work_item_team_then_membership_snapshot";

    private final WorkItemRepository workItemRepository;
    private final ProjectRepository projectRepository;
    private final ProgramRepository programRepository;
    private final ProgramProjectRepository programProjectRepository;
    private final IterationRepository iterationRepository;
    private final TeamRepository teamRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final WorkItemStatusHistoryRepository workItemStatusHistoryRepository;
    private final WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository;
    private final WorkItemEstimateHistoryRepository workItemEstimateHistoryRepository;
    private final WorkItemTeamHistoryRepository workItemTeamHistoryRepository;
    private final WorkLogRepository workLogRepository;
    private final IterationSnapshotRepository iterationSnapshotRepository;
    private final VelocitySnapshotRepository velocitySnapshotRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ReportingSnapshotService reportingSnapshotService;

    public ReportingService(
            WorkItemRepository workItemRepository,
            ProjectRepository projectRepository,
            ProgramRepository programRepository,
            ProgramProjectRepository programProjectRepository,
            IterationRepository iterationRepository,
            TeamRepository teamRepository,
            ProjectTeamRepository projectTeamRepository,
            WorkItemStatusHistoryRepository workItemStatusHistoryRepository,
            WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository,
            WorkItemEstimateHistoryRepository workItemEstimateHistoryRepository,
            WorkItemTeamHistoryRepository workItemTeamHistoryRepository,
            WorkLogRepository workLogRepository,
            IterationSnapshotRepository iterationSnapshotRepository,
            VelocitySnapshotRepository velocitySnapshotRepository,
            WorkspaceRepository workspaceRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ReportingSnapshotService reportingSnapshotService
    ) {
        this.workItemRepository = workItemRepository;
        this.projectRepository = projectRepository;
        this.programRepository = programRepository;
        this.programProjectRepository = programProjectRepository;
        this.iterationRepository = iterationRepository;
        this.teamRepository = teamRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.workItemStatusHistoryRepository = workItemStatusHistoryRepository;
        this.workItemAssignmentHistoryRepository = workItemAssignmentHistoryRepository;
        this.workItemEstimateHistoryRepository = workItemEstimateHistoryRepository;
        this.workItemTeamHistoryRepository = workItemTeamHistoryRepository;
        this.workLogRepository = workLogRepository;
        this.iterationSnapshotRepository = iterationSnapshotRepository;
        this.velocitySnapshotRepository = velocitySnapshotRepository;
        this.workspaceRepository = workspaceRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.reportingSnapshotService = reportingSnapshotService;
    }

    @Transactional(readOnly = true)
    public List<WorkItemStatusHistoryResponse> statusHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemStatusHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemStatusHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkItemAssignmentHistoryResponse> assignmentHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemAssignmentHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemAssignmentHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkItemEstimateHistoryResponse> estimateHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemEstimateHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemEstimateHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkItemTeamHistoryResponse> teamHistory(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        return workItemTeamHistoryRepository.findByWorkItemIdOrderByChangedAtAsc(item.getId()).stream()
                .map(WorkItemTeamHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkLogSummaryResponse workLogSummary(UUID workItemId) {
        WorkItem item = reportableWorkItem(workItemId);
        requireReportRead(item);
        List<WorkLog> workLogs = workLogRepository.findByWorkItemIdAndDeletedAtIsNullOrderByWorkDateDescCreatedAtDesc(item.getId());
        Map<UUID, UserWorkLogAccumulator> userTotals = new LinkedHashMap<>();
        long totalMinutes = 0;
        for (WorkLog workLog : workLogs) {
            int minutes = workLog.getMinutesSpent() == null ? 0 : workLog.getMinutesSpent();
            totalMinutes += minutes;
            userTotals.computeIfAbsent(workLog.getUserId(), UserWorkLogAccumulator::new).add(minutes);
        }
        return new WorkLogSummaryResponse(
                item.getId(),
                workLogs.size(),
                totalMinutes,
                userTotals.values().stream()
                        .map(UserWorkLogAccumulator::response)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ProjectReportSummaryResponse projectDashboardSummary(UUID projectId, String from, String to, UUID teamId, UUID iterationId) {
        Project project = reportableProject(projectId);
        requireReportRead(project);
        ReportWindow window = reportWindow(from, to);
        ReportScope scope = reportScope(project, window, teamId, iterationId);
        ScopedReportQuery scoped = scopedReportQuery(scope, window);

        ProjectReportSummaryResponse.WorkItemMetricsResponse workItems = workItemMetrics(scoped);
        ProjectReportSummaryResponse.ThroughputMetricsResponse throughput = throughputMetrics(scoped);
        ProjectReportSummaryResponse.EstimateTimeMetricsResponse estimateAndTime = estimateAndTimeMetrics(scoped);
        ProjectReportSummaryResponse.AgingMetricsResponse aging = agingMetrics(scoped);
        ProjectReportSummaryResponse.CycleTimeMetricsResponse cycleTime = cycleTimeMetrics(scoped);
        List<ProjectReportSummaryResponse.DimensionCountResponse> byStatus = dimensionCounts(scoped, "status");
        List<ProjectReportSummaryResponse.DimensionCountResponse> byType = dimensionCounts(scoped, "type");
        List<ProjectReportSummaryResponse.DimensionCountResponse> byPriority = dimensionCounts(scoped, "priority");

        List<ProjectReportSummaryResponse.ReportWidgetResponse> widgets = List.of(
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_item_summary", workItems),
                new ProjectReportSummaryResponse.ReportWidgetResponse("throughput", throughput),
                new ProjectReportSummaryResponse.ReportWidgetResponse("estimate_time_summary", estimateAndTime),
                new ProjectReportSummaryResponse.ReportWidgetResponse("aging_wip", aging),
                new ProjectReportSummaryResponse.ReportWidgetResponse("cycle_time_inputs", cycleTime),
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_by_status", byStatus),
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_by_type", byType),
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_by_priority", byPriority)
        );

        return new ProjectReportSummaryResponse(
                project.getId(),
                project.getWorkspaceId(),
                new ProjectReportSummaryResponse.ReportScopeResponse(
                        scope.scopeType(),
                        scope.teamId(),
                        scope.iterationId(),
                        scope.teamId() == null ? null : TEAM_RESOLUTION_MODE
                ),
                window.from(),
                window.to(),
                workItems,
                throughput,
                estimateAndTime,
                aging,
                cycleTime,
                byStatus,
                byType,
                byPriority,
                widgets
        );
    }

    @Transactional(readOnly = true)
    public PortfolioReportSummaryResponse workspaceDashboardSummary(UUID workspaceId, String from, String to, List<UUID> projectIds) {
        Workspace workspace = reportableWorkspace(workspaceId);
        requireWorkspaceReportRead(workspace.getId());
        ReportWindow window = reportWindow(from, to);
        List<UUID> scopedProjectIds = validatedProjectIds(workspace.getId(), projectIds);
        return portfolioSummary(new PortfolioReportScope(workspace.getId(), "workspace", null, scopedProjectIds), window);
    }

    @Transactional(readOnly = true)
    public PortfolioReportSummaryResponse programDashboardSummary(UUID programId, String from, String to) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));
        if (!"active".equals(program.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found");
        }
        reportableWorkspace(program.getWorkspaceId());
        requireWorkspaceReportRead(program.getWorkspaceId());
        ReportWindow window = reportWindow(from, to);
        List<UUID> projectIds = programProjectRepository.findByIdProgramIdOrderByPositionAscCreatedAtAsc(program.getId()).stream()
                .map(ProgramProject::getId)
                .map(com.strangequark.trasck.project.ProgramProjectId::getProjectId)
                .toList();
        return portfolioSummary(new PortfolioReportScope(program.getWorkspaceId(), "program", program.getId(), projectIds), window);
    }

    @Transactional
    public ReportingSnapshotRunResponse runWorkspaceSnapshots(UUID workspaceId, String snapshotDate) {
        Workspace workspace = reportableWorkspace(workspaceId);
        requireWorkspaceReportRead(workspace.getId());
        LocalDate effectiveSnapshotDate = hasText(snapshotDate)
                ? parseDate(snapshotDate, "date")
                : LocalDate.now(ZoneOffset.UTC);
        return reportingSnapshotService.runWorkspaceSnapshots(workspaceId, effectiveSnapshotDate);
    }

    @Transactional
    public ReportingSnapshotBackfillResponse backfillWorkspaceSnapshots(UUID workspaceId, String fromDate, String toDate, String action) {
        Workspace workspace = reportableWorkspace(workspaceId);
        requireWorkspaceReportRead(workspace.getId());
        LocalDate effectiveTo = hasText(toDate) ? parseDate(toDate, "toDate") : LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = hasText(fromDate) ? parseDate(fromDate, "fromDate") : effectiveTo;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate");
        }
        long days = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;
        if (days > 370) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "snapshot backfill is limited to 370 days per request");
        }
        List<ReportingSnapshotRunResponse> runs = new ArrayList<>();
        int cycleTimeRecords = 0;
        int iterationSnapshots = 0;
        int velocitySnapshots = 0;
        int cumulativeFlowSnapshots = 0;
        for (LocalDate date = effectiveFrom; !date.isAfter(effectiveTo); date = date.plusDays(1)) {
            ReportingSnapshotRunResponse run = reportingSnapshotService.runWorkspaceSnapshots(workspaceId, date);
            runs.add(run);
            cycleTimeRecords += run.cycleTimeRecords();
            iterationSnapshots += run.iterationSnapshots();
            velocitySnapshots += run.velocitySnapshots();
            cumulativeFlowSnapshots += run.cumulativeFlowSnapshots();
        }
        return new ReportingSnapshotBackfillResponse(
                workspaceId,
                normalizeSnapshotAction(action),
                effectiveFrom,
                effectiveTo,
                runs.size(),
                cycleTimeRecords,
                iterationSnapshots,
                velocitySnapshots,
                cumulativeFlowSnapshots,
                runs
        );
    }

    @Transactional(readOnly = true)
    public ProjectReportingSnapshotsResponse projectSnapshots(UUID projectId, String fromDate, String toDate) {
        Project project = reportableProject(projectId);
        requireReportRead(project);
        SnapshotWindow window = snapshotWindow(fromDate, toDate);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", project.getId())
                .addValue("fromDate", window.fromDate())
                .addValue("toDate", window.toDate());

        List<CycleTimeRecordResponse> cycleTimeRecords = namedJdbcTemplate.query("""
                select ctr.id,
                       ctr.work_item_id,
                       ctr.created_at,
                       ctr.started_at,
                       ctr.completed_at,
                       ctr.lead_time_minutes,
                       ctr.cycle_time_minutes
                from cycle_time_records ctr
                join work_items wi on wi.id = ctr.work_item_id
                where wi.project_id = :projectId
                  and ctr.completed_at::date >= :fromDate
                  and ctr.completed_at::date <= :toDate
                order by ctr.completed_at asc, ctr.work_item_id asc
                """, params, (rs, rowNum) -> new CycleTimeRecordResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("work_item_id"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                integer(rs, "lead_time_minutes"),
                integer(rs, "cycle_time_minutes")
        ));
        List<IterationSnapshotResponse> iterationSnapshots = namedJdbcTemplate.query("""
                select snapshot.id,
                       snapshot.iteration_id,
                       snapshot.snapshot_date,
                       snapshot.committed_points,
                       snapshot.completed_points,
                       snapshot.remaining_points,
                       snapshot.scope_added_points,
                       snapshot.scope_removed_points
                from iteration_snapshots snapshot
                join iterations iteration on iteration.id = snapshot.iteration_id
                where iteration.project_id = :projectId
                  and snapshot.snapshot_date >= :fromDate
                  and snapshot.snapshot_date <= :toDate
                order by snapshot.snapshot_date asc, snapshot.iteration_id asc
                """, params, (rs, rowNum) -> new IterationSnapshotResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("iteration_id"),
                rs.getObject("snapshot_date", LocalDate.class),
                decimal(rs.getBigDecimal("committed_points")),
                decimal(rs.getBigDecimal("completed_points")),
                decimal(rs.getBigDecimal("remaining_points")),
                decimal(rs.getBigDecimal("scope_added_points")),
                decimal(rs.getBigDecimal("scope_removed_points"))
        ));
        List<VelocitySnapshotResponse> velocitySnapshots = namedJdbcTemplate.query("""
                select snapshot.id,
                       snapshot.team_id,
                       snapshot.iteration_id,
                       coalesce(iteration.end_date, iteration.start_date) as snapshot_date,
                       snapshot.committed_points,
                       snapshot.completed_points,
                       snapshot.carried_over_points
                from velocity_snapshots snapshot
                join iterations iteration on iteration.id = snapshot.iteration_id
                where iteration.project_id = :projectId
                  and (iteration.start_date is null or iteration.start_date <= :toDate)
                  and (iteration.end_date is null or iteration.end_date >= :fromDate)
                order by iteration.start_date asc nulls last, iteration.name asc, snapshot.team_id asc
                """, params, (rs, rowNum) -> new VelocitySnapshotResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("team_id"),
                (UUID) rs.getObject("iteration_id"),
                rs.getObject("snapshot_date", LocalDate.class),
                decimal(rs.getBigDecimal("committed_points")),
                decimal(rs.getBigDecimal("completed_points")),
                decimal(rs.getBigDecimal("carried_over_points"))
        ));
        List<CumulativeFlowSnapshotResponse> cumulativeFlowSnapshots = namedJdbcTemplate.query("""
                select snapshot.id,
                       snapshot.board_id,
                       snapshot.snapshot_date,
                       snapshot.status_id,
                       snapshot.work_item_count,
                       snapshot.total_points
                from cumulative_flow_snapshots snapshot
                join boards board on board.id = snapshot.board_id
                where board.project_id = :projectId
                  and snapshot.snapshot_date >= :fromDate
                  and snapshot.snapshot_date <= :toDate
                order by snapshot.snapshot_date asc, snapshot.board_id asc, snapshot.status_id asc
                """, params, (rs, rowNum) -> new CumulativeFlowSnapshotResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("board_id"),
                rs.getObject("snapshot_date", LocalDate.class),
                (UUID) rs.getObject("status_id"),
                integer(rs, "work_item_count"),
                decimal(rs.getBigDecimal("total_points"))
        ));
        return new ProjectReportingSnapshotsResponse(
                project.getId(),
                project.getWorkspaceId(),
                window.fromDate(),
                window.toDate(),
                cycleTimeRecords,
                iterationSnapshots,
                velocitySnapshots,
                cumulativeFlowSnapshots,
                snapshotSeries(cycleTimeRecords, iterationSnapshots, velocitySnapshots, cumulativeFlowSnapshots)
        );
    }

    @Transactional(readOnly = true)
    public IterationReportResponse iterationReport(UUID iterationId, String source) {
        Iteration iteration = iterationRepository.findById(iterationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Iteration not found"));
        UUID projectId = iteration.getProjectId();
        if (projectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Iteration reports require a project-scoped iteration");
        }
        Project project = reportableProject(projectId);
        requireReportRead(project);
        String effectiveSource = normalizeIterationReportSource(source);
        IterationReportResponse.IterationMetrics live = "snapshot".equals(effectiveSource) ? null : liveIterationMetrics(iteration);
        List<IterationSnapshot> snapshots = "live".equals(effectiveSource)
                ? List.of()
                : iterationSnapshotRepository.findByIterationIdOrderBySnapshotDateAsc(iteration.getId());
        List<VelocitySnapshot> velocitySnapshots = "live".equals(effectiveSource)
                ? List.of()
                : velocitySnapshotRepository.findByIterationId(iteration.getId());
        IterationReportResponse.IterationMetrics latestSnapshot = snapshots.isEmpty()
                ? null
                : snapshotMetrics(snapshots.get(snapshots.size() - 1));
        return new IterationReportResponse(
                iteration.getId(),
                project.getId(),
                iteration.getTeamId(),
                effectiveSource,
                live,
                latestSnapshot,
                snapshots.stream().map(IterationSnapshotResponse::from).toList(),
                velocitySnapshots.stream().map(VelocitySnapshotResponse::from).toList()
        );
    }

    private SnapshotWindow snapshotWindow(String fromDate, String toDate) {
        LocalDate effectiveTo = hasText(toDate) ? parseDate(toDate, "toDate") : LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = hasText(fromDate) ? parseDate(fromDate, "fromDate") : effectiveTo.minusDays(DEFAULT_REPORT_DAYS);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must be on or before toDate");
        }
        return new SnapshotWindow(effectiveFrom, effectiveTo);
    }

    private String normalizeSnapshotAction(String action) {
        if (!hasText(action)) {
            return "backfill";
        }
        String normalized = action.trim().toLowerCase();
        if (!List.of("backfill", "reconcile").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action must be backfill or reconcile");
        }
        return normalized;
    }

    private String normalizeIterationReportSource(String source) {
        String normalized = hasText(source) ? source.trim().toLowerCase() : "both";
        if (!List.of("live", "snapshot", "both").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source must be live, snapshot, or both");
        }
        return normalized;
    }

    private IterationReportResponse.IterationMetrics liveIterationMetrics(Iteration iteration) {
        return namedJdbcTemplate.queryForObject("""
                select coalesce(iteration.committed_points, 0) as committed_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at is null
                             and coalesce(status.terminal, false) = true
                       ), 0) as completed_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at is null
                             and coalesce(status.terminal, false) = false
                       ), 0) as remaining_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at is null
                       ), 0) as scoped_points,
                       coalesce(sum(coalesce(work_item.estimate_points, 0)) filter (
                           where iteration_work_item.removed_at is not null
                       ), 0) as removed_points,
                       count(work_item.id) filter (where iteration_work_item.removed_at is null) as scoped_work_items,
                       count(work_item.id) filter (
                           where iteration_work_item.removed_at is null
                             and coalesce(status.terminal, false) = true
                       ) as completed_work_items
                from iterations iteration
                left join iteration_work_items iteration_work_item on iteration_work_item.iteration_id = iteration.id
                left join work_items work_item on work_item.id = iteration_work_item.work_item_id
                    and work_item.deleted_at is null
                left join workflow_statuses status on status.id = work_item.status_id
                where iteration.id = :iterationId
                group by iteration.id, iteration.committed_points
                """, new MapSqlParameterSource().addValue("iterationId", iteration.getId()), (rs, rowNum) -> {
            BigDecimal scopedPoints = decimal(rs.getBigDecimal("scoped_points"));
            return new IterationReportResponse.IterationMetrics(
                    decimal(rs.getBigDecimal("committed_points")),
                    decimal(rs.getBigDecimal("completed_points")),
                    decimal(rs.getBigDecimal("remaining_points")),
                    scopedPoints,
                    decimal(rs.getBigDecimal("removed_points")),
                    rs.getLong("scoped_work_items"),
                    rs.getLong("completed_work_items")
            );
        });
    }

    private IterationReportResponse.IterationMetrics snapshotMetrics(IterationSnapshot snapshot) {
        return new IterationReportResponse.IterationMetrics(
                decimal(snapshot.getCommittedPoints()),
                decimal(snapshot.getCompletedPoints()),
                decimal(snapshot.getRemainingPoints()),
                decimal(snapshot.getScopeAddedPoints()),
                decimal(snapshot.getScopeRemovedPoints()),
                0,
                0
        );
    }

    private List<ReportingSnapshotSeriesPointResponse> snapshotSeries(
            List<CycleTimeRecordResponse> cycleTimeRecords,
            List<IterationSnapshotResponse> iterationSnapshots,
            List<VelocitySnapshotResponse> velocitySnapshots,
            List<CumulativeFlowSnapshotResponse> cumulativeFlowSnapshots
    ) {
        List<ReportingSnapshotSeriesPointResponse> series = new ArrayList<>();
        for (CycleTimeRecordResponse record : cycleTimeRecords) {
            LocalDate completedDate = record.completedAt() == null ? null : record.completedAt().toLocalDate();
            if (completedDate != null && record.leadTimeMinutes() != null) {
                series.add(new ReportingSnapshotSeriesPointResponse(
                        completedDate,
                        "cycle_time.lead_time_minutes",
                        record.workItemId(),
                        "Lead time",
                        BigDecimal.valueOf(record.leadTimeMinutes())
                ));
            }
            if (completedDate != null && record.cycleTimeMinutes() != null) {
                series.add(new ReportingSnapshotSeriesPointResponse(
                        completedDate,
                        "cycle_time.cycle_time_minutes",
                        record.workItemId(),
                        "Cycle time",
                        BigDecimal.valueOf(record.cycleTimeMinutes())
                ));
            }
        }
        for (IterationSnapshotResponse snapshot : iterationSnapshots) {
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "iteration.committed_points", snapshot.iterationId(), "Committed points", snapshot.committedPoints()));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "iteration.completed_points", snapshot.iterationId(), "Completed points", snapshot.completedPoints()));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "iteration.remaining_points", snapshot.iterationId(), "Remaining points", snapshot.remainingPoints()));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "iteration.scope_added_points", snapshot.iterationId(), "Scope added points", snapshot.scopeAddedPoints()));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "iteration.scope_removed_points", snapshot.iterationId(), "Scope removed points", snapshot.scopeRemovedPoints()));
        }
        for (VelocitySnapshotResponse snapshot : velocitySnapshots) {
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "velocity.committed_points", snapshot.iterationId(), "Committed points", snapshot.committedPoints()));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "velocity.completed_points", snapshot.iterationId(), "Completed points", snapshot.completedPoints()));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "velocity.carried_over_points", snapshot.iterationId(), "Carried-over points", snapshot.carriedOverPoints()));
        }
        for (CumulativeFlowSnapshotResponse snapshot : cumulativeFlowSnapshots) {
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "cumulative_flow.work_item_count", snapshot.statusId(), "Work item count", BigDecimal.valueOf(snapshot.workItemCount())));
            series.add(new ReportingSnapshotSeriesPointResponse(snapshot.snapshotDate(), "cumulative_flow.total_points", snapshot.statusId(), "Total points", snapshot.totalPoints()));
        }
        return series;
    }

    private Integer integer(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private WorkItem reportableWorkItem(UUID workItemId) {
        return workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
    }

    private Project reportableProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }

    private Workspace reportableWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return workspace;
    }

    private void requireReportRead(WorkItem item) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "report.read");
    }

    private void requireReportRead(Project project) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, project.getId(), "report.read");
    }

    private void requireWorkspaceReportRead(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.read");
    }

    private ReportWindow reportWindow(String from, String to) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime parsedTo = to == null || to.isBlank() ? now : parseUtcInstant(to, "to");
        OffsetDateTime parsedFrom = from == null || from.isBlank() ? parsedTo.minusDays(DEFAULT_REPORT_DAYS) : parseUtcInstant(from, "from");
        if (!parsedFrom.isBefore(parsedTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        LocalDate fromDate = parsedFrom.toLocalDate();
        LocalDate toDateExclusive = LocalTime.MIDNIGHT.equals(parsedTo.toLocalTime())
                ? parsedTo.toLocalDate()
                : parsedTo.toLocalDate().plusDays(1);
        if (!fromDate.isBefore(toDateExclusive)) {
            toDateExclusive = fromDate.plusDays(1);
        }
        return new ReportWindow(parsedFrom, parsedTo, fromDate, toDateExclusive);
    }

    private OffsetDateTime parseUtcInstant(String value, String fieldName) {
        try {
            return OffsetDateTime.parse(value.trim()).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be an ISO-8601 offset date-time");
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be an ISO-8601 date");
        }
    }

    private ReportScope reportScope(Project project, ReportWindow window, UUID requestedTeamId, UUID iterationId) {
        UUID effectiveTeamId = requestedTeamId;
        Iteration iteration = null;
        if (iterationId != null) {
            iteration = iterationRepository.findByIdAndWorkspaceId(iterationId, project.getWorkspaceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Iteration not found in this workspace"));
            if (iteration.getProjectId() != null && !project.getId().equals(iteration.getProjectId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Iteration does not belong to this project");
            }
            if (iteration.getTeamId() != null) {
                if (effectiveTeamId != null && !effectiveTeamId.equals(iteration.getTeamId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Iteration team does not match the requested team");
                }
                effectiveTeamId = iteration.getTeamId();
            }
        }

        if (effectiveTeamId != null) {
            Team team = teamRepository.findByIdAndWorkspaceId(effectiveTeamId, project.getWorkspaceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team not found in this workspace"));
            if (!"active".equals(team.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team is not active");
            }
            if (!projectTeamRepository.existsByIdProjectIdAndIdTeamId(project.getId(), team.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team is not assigned to this project");
            }
        }

        OffsetDateTime membershipFrom = window.from();
        OffsetDateTime membershipTo = window.to();
        if (iteration != null) {
            if (iteration.getStartDate() != null) {
                membershipFrom = iteration.getStartDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            }
            if (iteration.getEndDate() != null) {
                membershipTo = iteration.getEndDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            }
        }

        String scopeType;
        if (iterationId != null) {
            scopeType = "iteration";
        } else if (effectiveTeamId != null) {
            scopeType = "team";
        } else {
            scopeType = "project";
        }
        return new ReportScope(project.getId(), project.getWorkspaceId(), effectiveTeamId, iterationId, scopeType, membershipFrom, membershipTo);
    }

    private ScopedReportQuery scopedReportQuery(ReportScope scope, ReportWindow window) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", scope.projectId())
                .addValue("workspaceId", scope.workspaceId())
                .addValue("from", window.from())
                .addValue("to", window.to())
                .addValue("fromDate", window.fromDate())
                .addValue("toDateExclusive", window.toDateExclusive())
                .addValue("staleDays", STALE_OPEN_WORK_DAYS);

        StringBuilder cte = new StringBuilder("""
                with scoped_work_items as (
                    select wi.*
                    from work_items wi
                    where wi.project_id = :projectId
                      and wi.deleted_at is null
                """);
        if (scope.iterationId() != null) {
            params.addValue("iterationId", scope.iterationId());
            cte.append("""
                      and exists (
                          select 1
                          from iteration_work_items iwi
                          where iwi.work_item_id = wi.id
                            and iwi.iteration_id = :iterationId
                            and iwi.removed_at is null
                      )
                    """);
        }
        if (scope.teamId() != null) {
            params.addValue("teamId", scope.teamId());
            params.addValue("membershipFrom", scope.membershipFrom());
            params.addValue("membershipTo", scope.membershipTo());
            cte.append("""
                      and (
                          wi.team_id = :teamId
                          or (
                              wi.team_id is null
                              and wi.assignee_id is not null
                              and exists (
                                  select 1
                                  from team_memberships tm
                                  where tm.team_id = :teamId
                                    and tm.user_id = wi.assignee_id
                                    and tm.joined_at < :membershipTo
                                    and (tm.left_at is null or tm.left_at >= :membershipFrom)
                              )
                          )
                      )
                    """);
        }
        cte.append(")\n");
        return new ScopedReportQuery(cte.toString(), params);
    }

    private PortfolioReportSummaryResponse portfolioSummary(PortfolioReportScope scope, ReportWindow window) {
        ScopedReportQuery scoped = portfolioScopedReportQuery(scope, window);
        ProjectReportSummaryResponse.WorkItemMetricsResponse workItems = workItemMetrics(scoped);
        ProjectReportSummaryResponse.ThroughputMetricsResponse throughput = throughputMetrics(scoped);
        ProjectReportSummaryResponse.EstimateTimeMetricsResponse estimateAndTime = estimateAndTimeMetrics(scoped);
        ProjectReportSummaryResponse.CycleTimeMetricsResponse cycleTime = cycleTimeMetrics(scoped);
        List<ProjectReportSummaryResponse.DimensionCountResponse> byProject = portfolioProjectCounts(scoped);
        List<ProjectReportSummaryResponse.DimensionCountResponse> byTeam = portfolioTeamCounts(scoped);
        List<ProjectReportSummaryResponse.DimensionCountResponse> byType = dimensionCounts(scoped, "type");
        List<ProjectReportSummaryResponse.ReportWidgetResponse> widgets = List.of(
                new ProjectReportSummaryResponse.ReportWidgetResponse("portfolio_work_item_summary", workItems),
                new ProjectReportSummaryResponse.ReportWidgetResponse("portfolio_throughput", throughput),
                new ProjectReportSummaryResponse.ReportWidgetResponse("portfolio_estimate_time_summary", estimateAndTime),
                new ProjectReportSummaryResponse.ReportWidgetResponse("portfolio_cycle_time_inputs", cycleTime),
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_by_project", byProject),
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_by_team", byTeam),
                new ProjectReportSummaryResponse.ReportWidgetResponse("work_by_type", byType)
        );
        return new PortfolioReportSummaryResponse(
                scope.workspaceId(),
                new PortfolioReportSummaryResponse.PortfolioScopeResponse(scope.scopeType(), scope.programId(), scope.projectIds()),
                window.from(),
                window.to(),
                workItems,
                throughput,
                estimateAndTime,
                cycleTime,
                byProject,
                byTeam,
                byType,
                widgets
        );
    }

    private ScopedReportQuery portfolioScopedReportQuery(PortfolioReportScope scope, ReportWindow window) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspaceId", scope.workspaceId())
                .addValue("from", window.from())
                .addValue("to", window.to())
                .addValue("fromDate", window.fromDate())
                .addValue("toDateExclusive", window.toDateExclusive())
                .addValue("staleDays", STALE_OPEN_WORK_DAYS);
        StringBuilder cte = new StringBuilder("""
                with scoped_projects as (
                    select p.*
                    from projects p
                    where p.workspace_id = :workspaceId
                      and p.deleted_at is null
                      and p.status = 'active'
                """);
        if (scope.programId() != null) {
            params.addValue("programId", scope.programId());
            cte.append("""
                      and exists (
                          select 1
                          from program_projects pp
                          where pp.program_id = :programId
                            and pp.project_id = p.id
                      )
                    """);
        }
        if (scope.projectIds() != null && !scope.projectIds().isEmpty()) {
            params.addValue("projectIds", scope.projectIds());
            cte.append("""
                      and p.id in (:projectIds)
                    """);
        }
        cte.append("""
                ),
                scoped_work_items as (
                    select wi.*
                    from work_items wi
                    join scoped_projects sp on sp.id = wi.project_id
                    where wi.deleted_at is null
                )
                """);
        return new ScopedReportQuery(cte.toString(), params);
    }

    private List<UUID> validatedProjectIds(UUID workspaceId, List<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinctIds = projectIds.stream().distinct().toList();
        for (UUID projectId : distinctIds) {
            Project project = reportableProject(projectId);
            if (!workspaceId.equals(project.getWorkspaceId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project does not belong to this workspace");
            }
        }
        return distinctIds;
    }

    private ProjectReportSummaryResponse.WorkItemMetricsResponse workItemMetrics(ScopedReportQuery query) {
        return namedJdbcTemplate.queryForObject(query.cte() + """
                select count(*) as total,
                       count(*) filter (where coalesce(ws.terminal, false) = false) as open,
                       count(*) filter (where coalesce(ws.terminal, false) = true) as completed,
                       count(*) filter (where ws.key = 'blocked') as blocked
                from scoped_work_items wi
                join workflow_statuses ws on ws.id = wi.status_id
                """, query.params(), (rs, rowNum) -> new ProjectReportSummaryResponse.WorkItemMetricsResponse(
                rs.getLong("total"),
                rs.getLong("open"),
                rs.getLong("completed"),
                rs.getLong("blocked")
        ));
    }

    private ProjectReportSummaryResponse.ThroughputMetricsResponse throughputMetrics(ScopedReportQuery query) {
        return namedJdbcTemplate.queryForObject(query.cte() + """
                select count(distinct wi.id) filter (where wi.created_at >= :from and wi.created_at < :to) as created,
                       count(distinct wi.id) filter (
                           where (wi.resolved_at >= :from and wi.resolved_at < :to)
                              or exists (
                                  select 1
                                  from work_item_status_history h
                                  join workflow_statuses terminal_status on terminal_status.id = h.to_status_id
                                  where h.work_item_id = wi.id
                                    and terminal_status.terminal = true
                                    and h.changed_at >= :from
                                    and h.changed_at < :to
                              )
                       ) as completed,
                       (
                           select count(*)
                           from work_item_status_history h
                           join scoped_work_items transitioned on transitioned.id = h.work_item_id
                           where h.changed_at >= :from
                             and h.changed_at < :to
                       ) as status_transitions
                from scoped_work_items wi
                """, query.params(), (rs, rowNum) -> new ProjectReportSummaryResponse.ThroughputMetricsResponse(
                rs.getLong("created"),
                rs.getLong("completed"),
                rs.getLong("status_transitions")
        ));
    }

    private ProjectReportSummaryResponse.EstimateTimeMetricsResponse estimateAndTimeMetrics(ScopedReportQuery query) {
        return namedJdbcTemplate.queryForObject(query.cte() + """
                select (select coalesce(sum(estimate_points), 0) from scoped_work_items) as estimate_points,
                       (select coalesce(sum(estimate_minutes), 0) from scoped_work_items) as estimate_minutes,
                       (select coalesce(sum(remaining_minutes), 0) from scoped_work_items) as remaining_minutes,
                       (select count(*)
                        from work_logs wl
                        join scoped_work_items wi on wi.id = wl.work_item_id
                        where wl.deleted_at is null
                          and (
                              (wl.started_at is not null and wl.started_at >= :from and wl.started_at < :to)
                              or (wl.started_at is null and wl.work_date >= :fromDate and wl.work_date < :toDateExclusive)
                          )) as work_log_entry_count,
                       (select coalesce(sum(wl.minutes_spent), 0)
                        from work_logs wl
                        join scoped_work_items wi on wi.id = wl.work_item_id
                        where wl.deleted_at is null
                          and (
                              (wl.started_at is not null and wl.started_at >= :from and wl.started_at < :to)
                              or (wl.started_at is null and wl.work_date >= :fromDate and wl.work_date < :toDateExclusive)
                          )) as work_log_minutes,
                       (select count(distinct wl.user_id)
                        from work_logs wl
                        join scoped_work_items wi on wi.id = wl.work_item_id
                        where wl.deleted_at is null
                          and (
                              (wl.started_at is not null and wl.started_at >= :from and wl.started_at < :to)
                              or (wl.started_at is null and wl.work_date >= :fromDate and wl.work_date < :toDateExclusive)
                          )) as work_log_user_count
                """, query.params(), (rs, rowNum) -> new ProjectReportSummaryResponse.EstimateTimeMetricsResponse(
                decimal(rs.getBigDecimal("estimate_points")),
                rs.getLong("estimate_minutes"),
                rs.getLong("remaining_minutes"),
                rs.getLong("work_log_entry_count"),
                rs.getLong("work_log_minutes"),
                rs.getLong("work_log_user_count"),
                "soft_deleted_excluded"
        ));
    }

    private ProjectReportSummaryResponse.AgingMetricsResponse agingMetrics(ScopedReportQuery query) {
        return namedJdbcTemplate.queryForObject(query.cte() + """
                select count(*) filter (where wi.created_at < now() - (:staleDays * interval '1 day')) as stale_open_work_items,
                       coalesce(avg(extract(epoch from (now() - wi.created_at)) / 86400.0), 0) as average_open_age_days,
                       coalesce(max(extract(epoch from (now() - wi.created_at)) / 86400.0), 0) as oldest_open_age_days
                from scoped_work_items wi
                join workflow_statuses ws on ws.id = wi.status_id
                where coalesce(ws.terminal, false) = false
                """, query.params(), (rs, rowNum) -> new ProjectReportSummaryResponse.AgingMetricsResponse(
                rs.getLong("stale_open_work_items"),
                decimal(rs.getBigDecimal("average_open_age_days")),
                decimal(rs.getBigDecimal("oldest_open_age_days"))
        ));
    }

    private ProjectReportSummaryResponse.CycleTimeMetricsResponse cycleTimeMetrics(ScopedReportQuery query) {
        ProjectReportSummaryResponse.CycleTimeMetricsResponse snapshotMetrics = namedJdbcTemplate.queryForObject(query.cte() + """
                select count(*) as completed_work_items,
                       coalesce(round(avg(ctr.lead_time_minutes)), 0)::bigint as average_lead_time_minutes
                from scoped_work_items wi
                join cycle_time_records ctr on ctr.work_item_id = wi.id
                where ctr.completed_at >= :from
                  and ctr.completed_at < :to
                """, query.params(), (rs, rowNum) -> new ProjectReportSummaryResponse.CycleTimeMetricsResponse(
                rs.getLong("completed_work_items"),
                rs.getLong("average_lead_time_minutes")
        ));
        if (snapshotMetrics.completedWorkItems() > 0) {
            return snapshotMetrics;
        }
        return namedJdbcTemplate.queryForObject(query.cte() + """
                select count(*) as completed_work_items,
                       coalesce(round(avg(extract(epoch from (coalesce(wi.resolved_at, completed.completed_at) - wi.created_at)) / 60.0)), 0)::bigint as average_lead_time_minutes
                from scoped_work_items wi
                left join lateral (
                    select min(h.changed_at) as completed_at
                    from work_item_status_history h
                    join workflow_statuses terminal_status on terminal_status.id = h.to_status_id
                    where h.work_item_id = wi.id
                      and terminal_status.terminal = true
                      and h.changed_at >= :from
                      and h.changed_at < :to
                ) completed on true
                where (wi.resolved_at >= :from and wi.resolved_at < :to)
                   or completed.completed_at is not null
                """, query.params(), (rs, rowNum) -> new ProjectReportSummaryResponse.CycleTimeMetricsResponse(
                rs.getLong("completed_work_items"),
                rs.getLong("average_lead_time_minutes")
        ));
    }

    private List<ProjectReportSummaryResponse.DimensionCountResponse> dimensionCounts(ScopedReportQuery query, String dimension) {
        return switch (dimension) {
            case "status" -> namedJdbcTemplate.query(query.cte() + """
                    select ws.id,
                           ws.key,
                           ws.name,
                           ws.category,
                           count(*) as count,
                           coalesce(sum(wi.estimate_points), 0) as estimate_points,
                           coalesce(sum(wi.estimate_minutes), 0) as estimate_minutes,
                           coalesce(sum(wi.remaining_minutes), 0) as remaining_minutes
                    from scoped_work_items wi
                    join workflow_statuses ws on ws.id = wi.status_id
                    group by ws.id, ws.key, ws.name, ws.category, ws.sort_order
                    order by count desc, ws.sort_order asc, ws.name asc
                    """, query.params(), this::dimensionCount);
            case "type" -> namedJdbcTemplate.query(query.cte() + """
                    select wit.id,
                           wit.key,
                           wit.name,
                           wit.hierarchy_level::varchar as category,
                           count(*) as count,
                           coalesce(sum(wi.estimate_points), 0) as estimate_points,
                           coalesce(sum(wi.estimate_minutes), 0) as estimate_minutes,
                           coalesce(sum(wi.remaining_minutes), 0) as remaining_minutes
                    from scoped_work_items wi
                    join work_item_types wit on wit.id = wi.type_id
                    group by wit.id, wit.key, wit.name, wit.hierarchy_level
                    order by count desc, wit.hierarchy_level desc, wit.name asc
                    """, query.params(), this::dimensionCount);
            case "priority" -> namedJdbcTemplate.query(query.cte() + """
                    select p.id,
                           p.key,
                           p.name,
                           p.color as category,
                           count(*) as count,
                           coalesce(sum(wi.estimate_points), 0) as estimate_points,
                           coalesce(sum(wi.estimate_minutes), 0) as estimate_minutes,
                           coalesce(sum(wi.remaining_minutes), 0) as remaining_minutes
                    from scoped_work_items wi
                    left join priorities p on p.id = wi.priority_id
                    group by p.id, p.key, p.name, p.color, p.sort_order
                    order by count desc, p.sort_order asc nulls last, p.name asc nulls last
                    """, query.params(), this::dimensionCount);
            default -> List.of();
        };
    }

    private List<ProjectReportSummaryResponse.DimensionCountResponse> portfolioProjectCounts(ScopedReportQuery query) {
        return namedJdbcTemplate.query(query.cte() + """
                select sp.id,
                       sp.key,
                       sp.name,
                       sp.status as category,
                       count(wi.id) as count,
                       coalesce(sum(wi.estimate_points), 0) as estimate_points,
                       coalesce(sum(wi.estimate_minutes), 0) as estimate_minutes,
                       coalesce(sum(wi.remaining_minutes), 0) as remaining_minutes,
                       min(wi.workspace_sequence_number) as first_workspace_sequence
                from scoped_projects sp
                left join scoped_work_items wi on wi.project_id = sp.id
                group by sp.id, sp.key, sp.name, sp.status
                order by first_workspace_sequence asc nulls last, sp.key asc
                """, query.params(), this::dimensionCount);
    }

    private List<ProjectReportSummaryResponse.DimensionCountResponse> portfolioTeamCounts(ScopedReportQuery query) {
        return namedJdbcTemplate.query(query.cte() + """
                select t.id,
                       null::varchar as key,
                       coalesce(t.name, 'Unassigned') as name,
                       coalesce(t.status, 'unassigned') as category,
                       count(wi.id) as count,
                       coalesce(sum(wi.estimate_points), 0) as estimate_points,
                       coalesce(sum(wi.estimate_minutes), 0) as estimate_minutes,
                       coalesce(sum(wi.remaining_minutes), 0) as remaining_minutes,
                       min(wi.workspace_sequence_number) as first_workspace_sequence
                from scoped_work_items wi
                left join teams t on t.id = wi.team_id
                group by t.id, t.name, t.status
                order by first_workspace_sequence asc nulls last, name asc
                """, query.params(), this::dimensionCount);
    }

    private ProjectReportSummaryResponse.DimensionCountResponse dimensionCount(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectReportSummaryResponse.DimensionCountResponse(
                (UUID) rs.getObject("id"),
                rs.getString("key"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getLong("count"),
                decimal(rs.getBigDecimal("estimate_points")),
                rs.getLong("estimate_minutes"),
                rs.getLong("remaining_minutes")
        );
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ReportWindow(OffsetDateTime from, OffsetDateTime to, LocalDate fromDate, LocalDate toDateExclusive) {
    }

    private record SnapshotWindow(LocalDate fromDate, LocalDate toDate) {
    }

    private record ReportScope(
            UUID projectId,
            UUID workspaceId,
            UUID teamId,
            UUID iterationId,
            String scopeType,
            OffsetDateTime membershipFrom,
            OffsetDateTime membershipTo
    ) {
    }

    private record ScopedReportQuery(String cte, MapSqlParameterSource params) {
    }

    private record PortfolioReportScope(UUID workspaceId, String scopeType, UUID programId, List<UUID> projectIds) {
    }

    private static class UserWorkLogAccumulator {
        private final UUID userId;
        private int entryCount;
        private long totalMinutes;

        UserWorkLogAccumulator(UUID userId) {
            this.userId = userId;
        }

        void add(int minutes) {
            entryCount++;
            totalMinutes += minutes;
        }

        WorkLogSummaryResponse.UserWorkLogSummaryResponse response() {
            return new WorkLogSummaryResponse.UserWorkLogSummaryResponse(userId, entryCount, totalMinutes);
        }
    }
}
