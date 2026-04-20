package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/work-items/{workItemId}/status-history")
    public List<WorkItemStatusHistoryResponse> statusHistory(@PathVariable UUID workItemId) {
        return reportingService.statusHistory(workItemId);
    }

    @GetMapping("/work-items/{workItemId}/assignment-history")
    public List<WorkItemAssignmentHistoryResponse> assignmentHistory(@PathVariable UUID workItemId) {
        return reportingService.assignmentHistory(workItemId);
    }

    @GetMapping("/work-items/{workItemId}/estimate-history")
    public List<WorkItemEstimateHistoryResponse> estimateHistory(@PathVariable UUID workItemId) {
        return reportingService.estimateHistory(workItemId);
    }

    @GetMapping("/work-items/{workItemId}/team-history")
    public List<WorkItemTeamHistoryResponse> teamHistory(@PathVariable UUID workItemId) {
        return reportingService.teamHistory(workItemId);
    }

    @GetMapping("/work-items/{workItemId}/work-log-summary")
    public WorkLogSummaryResponse workLogSummary(@PathVariable UUID workItemId) {
        return reportingService.workLogSummary(workItemId);
    }

    @GetMapping("/projects/{projectId}/dashboard-summary")
    public ProjectReportSummaryResponse projectDashboardSummary(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID iterationId
    ) {
        return reportingService.projectDashboardSummary(projectId, from, to, teamId, iterationId);
    }

    @GetMapping("/projects/{projectId}/imports/completions")
    public ProjectReportSummaryResponse.ImportCompletionMetricsResponse projectImportCompletions(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID iterationId
    ) {
        return reportingService.projectImportCompletions(projectId, from, to, teamId, iterationId);
    }

    @GetMapping("/workspaces/{workspaceId}/dashboard-summary")
    public PortfolioReportSummaryResponse workspaceDashboardSummary(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> projectIds
    ) {
        return reportingService.workspaceDashboardSummary(workspaceId, from, to, projectIds);
    }

    @GetMapping("/workspaces/{workspaceId}/imports/completions")
    public ProjectReportSummaryResponse.ImportCompletionMetricsResponse workspaceImportCompletions(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> projectIds
    ) {
        return reportingService.workspaceImportCompletions(workspaceId, from, to, projectIds);
    }

    @GetMapping("/programs/{programId}/dashboard-summary")
    public PortfolioReportSummaryResponse programDashboardSummary(
            @PathVariable UUID programId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return reportingService.programDashboardSummary(programId, from, to);
    }

    @PostMapping("/workspaces/{workspaceId}/snapshots/run")
    public ReportingSnapshotRunResponse runWorkspaceSnapshots(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String date
    ) {
        return reportingService.runWorkspaceSnapshots(workspaceId, date);
    }

    @PostMapping("/workspaces/{workspaceId}/snapshots/backfill")
    public ReportingSnapshotBackfillResponse backfillWorkspaceSnapshots(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {
        return reportingService.backfillWorkspaceSnapshots(workspaceId, fromDate, toDate, "backfill");
    }

    @PostMapping("/workspaces/{workspaceId}/snapshots/reconcile")
    public ReportingSnapshotBackfillResponse reconcileWorkspaceSnapshots(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {
        return reportingService.backfillWorkspaceSnapshots(workspaceId, fromDate, toDate, "reconcile");
    }

    @GetMapping("/workspaces/{workspaceId}/snapshot-retention-policy")
    public ReportingRetentionPolicyResponse getSnapshotRetentionPolicy(@PathVariable UUID workspaceId) {
        return reportingService.getSnapshotRetentionPolicy(workspaceId);
    }

    @PutMapping("/workspaces/{workspaceId}/snapshot-retention-policy")
    public ReportingRetentionPolicyResponse updateSnapshotRetentionPolicy(
            @PathVariable UUID workspaceId,
            @RequestBody ReportingRetentionPolicyRequest request
    ) {
        return reportingService.updateSnapshotRetentionPolicy(workspaceId, request);
    }

    @PostMapping("/workspaces/{workspaceId}/snapshots/rollups/run")
    public ReportingRollupRunResponse runWorkspaceRollups(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String granularity
    ) {
        return reportingService.runWorkspaceRollups(workspaceId, fromDate, toDate, granularity);
    }

    @PostMapping("/workspaces/{workspaceId}/snapshots/rollups/backfill")
    public ReportingRollupRunResponse backfillWorkspaceRollups(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String granularity
    ) {
        return reportingService.backfillWorkspaceRollups(workspaceId, fromDate, toDate, granularity);
    }

    @GetMapping("/projects/{projectId}/snapshots")
    public ProjectReportingSnapshotsResponse projectSnapshots(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {
        return reportingService.projectSnapshots(projectId, fromDate, toDate);
    }

    @GetMapping("/iterations/{iterationId}/report")
    public IterationReportResponse iterationReport(
            @PathVariable UUID iterationId,
            @RequestParam(required = false) String source
    ) {
        return reportingService.iterationReport(iterationId, source);
    }
}
