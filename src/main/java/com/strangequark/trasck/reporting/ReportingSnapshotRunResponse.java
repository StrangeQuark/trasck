package com.strangequark.trasck.reporting;

import java.time.LocalDate;
import java.util.UUID;

public record ReportingSnapshotRunResponse(
        UUID workspaceId,
        LocalDate snapshotDate,
        int cycleTimeRecords,
        int iterationSnapshots,
        int velocitySnapshots,
        int cumulativeFlowSnapshots
) {
}
