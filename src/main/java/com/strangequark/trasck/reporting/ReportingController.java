package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/work-items/{workItemId}/work-log-summary")
    public WorkLogSummaryResponse workLogSummary(@PathVariable UUID workItemId) {
        return reportingService.workLogSummary(workItemId);
    }
}
