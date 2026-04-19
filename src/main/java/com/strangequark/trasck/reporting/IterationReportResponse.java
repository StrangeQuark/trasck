package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record IterationReportResponse(
        UUID iterationId,
        UUID projectId,
        UUID teamId,
        String source,
        IterationMetrics live,
        IterationMetrics latestSnapshot,
        List<IterationSnapshotResponse> snapshots,
        List<VelocitySnapshotResponse> velocitySnapshots
) {
    public record IterationMetrics(
            BigDecimal committedPoints,
            BigDecimal completedPoints,
            BigDecimal remainingPoints,
            BigDecimal scopeAddedPoints,
            BigDecimal scopeRemovedPoints,
            long scopedWorkItems,
            long completedWorkItems
    ) {
    }
}
