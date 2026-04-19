package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;

public record WorkLogSummaryResponse(
        UUID workItemId,
        int entryCount,
        long totalMinutes,
        List<UserWorkLogSummaryResponse> userTotals
) {
    public record UserWorkLogSummaryResponse(
            UUID userId,
            int entryCount,
            long totalMinutes
    ) {
    }
}
