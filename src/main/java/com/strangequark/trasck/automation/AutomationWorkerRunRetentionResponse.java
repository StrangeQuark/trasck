package com.strangequark.trasck.automation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AutomationWorkerRunRetentionResponse(
        UUID workspaceId,
        Boolean retentionEnabled,
        Integer retentionDays,
        Boolean exportBeforePrune,
        OffsetDateTime cutoff,
        long runsEligible,
        int runsIncluded,
        int runsPruned,
        UUID exportJobId,
        UUID fileAttachmentId,
        List<AutomationWorkerRunHistoryResponse> runs
) {
}
