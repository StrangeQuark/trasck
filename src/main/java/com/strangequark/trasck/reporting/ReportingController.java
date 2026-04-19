package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/workspaces/{workspaceId}/snapshots/run")
    public ReportingSnapshotRunResponse runWorkspaceSnapshots(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String date
    ) {
        return reportingService.runWorkspaceSnapshots(workspaceId, date);
    }
}
