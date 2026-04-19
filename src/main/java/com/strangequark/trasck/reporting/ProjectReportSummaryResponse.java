package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectReportSummaryResponse(
        UUID projectId,
        UUID workspaceId,
        ReportScopeResponse scope,
        OffsetDateTime from,
        OffsetDateTime to,
        WorkItemMetricsResponse workItems,
        ThroughputMetricsResponse throughput,
        EstimateTimeMetricsResponse estimateAndTime,
        AgingMetricsResponse aging,
        CycleTimeMetricsResponse cycleTime,
        List<DimensionCountResponse> byStatus,
        List<DimensionCountResponse> byType,
        List<DimensionCountResponse> byPriority,
        List<ReportWidgetResponse> widgets
) {
    public record ReportScopeResponse(
            String scopeType,
            UUID teamId,
            UUID iterationId,
            String teamResolutionMode
    ) {
    }

    public record WorkItemMetricsResponse(
            long total,
            long open,
            long completed,
            long blocked
    ) {
    }

    public record ThroughputMetricsResponse(
            long created,
            long completed,
            long statusTransitions
    ) {
    }

    public record EstimateTimeMetricsResponse(
            BigDecimal estimatePoints,
            long estimateMinutes,
            long remainingMinutes,
            long workLogEntryCount,
            long workLogMinutes,
            long workLogUserCount,
            String workLogDeletedBehavior
    ) {
    }

    public record AgingMetricsResponse(
            long staleOpenWorkItems,
            BigDecimal averageOpenAgeDays,
            BigDecimal oldestOpenAgeDays
    ) {
    }

    public record CycleTimeMetricsResponse(
            long completedWorkItems,
            long averageLeadTimeMinutes
    ) {
    }

    public record DimensionCountResponse(
            UUID id,
            String key,
            String name,
            String category,
            long count,
            BigDecimal estimatePoints,
            long estimateMinutes,
            long remainingMinutes
    ) {
    }

    public record ReportWidgetResponse(
            String widgetType,
            Object data
    ) {
    }
}
