package com.strangequark.trasck.reporting;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PortfolioReportSummaryResponse(
        UUID workspaceId,
        PortfolioScopeResponse scope,
        OffsetDateTime from,
        OffsetDateTime to,
        ProjectReportSummaryResponse.WorkItemMetricsResponse workItems,
        ProjectReportSummaryResponse.ThroughputMetricsResponse throughput,
        ProjectReportSummaryResponse.EstimateTimeMetricsResponse estimateAndTime,
        ProjectReportSummaryResponse.CycleTimeMetricsResponse cycleTime,
        ProjectReportSummaryResponse.ImportCompletionMetricsResponse importCompletions,
        List<ProjectReportSummaryResponse.DimensionCountResponse> byProject,
        List<ProjectReportSummaryResponse.DimensionCountResponse> byTeam,
        List<ProjectReportSummaryResponse.DimensionCountResponse> byType,
        List<ProjectReportSummaryResponse.ReportWidgetResponse> widgets
) {
    public record PortfolioScopeResponse(
            String scopeType,
            UUID programId,
            List<UUID> projectIds
    ) {
    }
}
