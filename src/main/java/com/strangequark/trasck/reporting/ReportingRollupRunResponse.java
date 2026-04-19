package com.strangequark.trasck.reporting;

import java.time.LocalDate;
import java.util.UUID;

public record ReportingRollupRunResponse(
        UUID workspaceId,
        UUID archiveRunId,
        String action,
        String granularity,
        LocalDate fromDate,
        LocalDate toDate,
        int cycleTimeRollups,
        int iterationRollups,
        int velocityRollups,
        int cumulativeFlowRollups,
        int genericRollups,
        int rawRowsPruned
) {
}
