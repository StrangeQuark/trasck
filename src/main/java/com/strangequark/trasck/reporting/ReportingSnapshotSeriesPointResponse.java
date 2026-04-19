package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReportingSnapshotSeriesPointResponse(
        LocalDate date,
        String metric,
        UUID entityId,
        String label,
        BigDecimal value
) {
}
