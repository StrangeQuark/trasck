package com.strangequark.trasck.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectReportingSnapshotsResponse(
        UUID projectId,
        UUID workspaceId,
        LocalDate fromDate,
        LocalDate toDate,
        List<CycleTimeRecordResponse> cycleTimeRecords,
        List<IterationSnapshotResponse> iterationSnapshots,
        List<VelocitySnapshotResponse> velocitySnapshots,
        List<CumulativeFlowSnapshotResponse> cumulativeFlowSnapshots,
        List<ReportingSnapshotSeriesPointResponse> series,
        List<ReportingSnapshotSeriesPointResponse> rollupSeries
) {
}
