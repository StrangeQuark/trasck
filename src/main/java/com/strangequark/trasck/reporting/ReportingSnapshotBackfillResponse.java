package com.strangequark.trasck.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportingSnapshotBackfillResponse(
        UUID workspaceId,
        String action,
        LocalDate fromDate,
        LocalDate toDate,
        int daysProcessed,
        int cycleTimeRecords,
        int iterationSnapshots,
        int velocitySnapshots,
        int cumulativeFlowSnapshots,
        List<ReportingSnapshotRunResponse> runs
) {
}
