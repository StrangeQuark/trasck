package com.strangequark.trasck.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CycleTimeRecordResponse(
        UUID id,
        UUID workItemId,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Integer leadTimeMinutes,
        Integer cycleTimeMinutes
) {
    static CycleTimeRecordResponse from(CycleTimeRecord record) {
        return new CycleTimeRecordResponse(
                record.getId(),
                record.getWorkItemId(),
                record.getCreatedAt(),
                record.getStartedAt(),
                record.getCompletedAt(),
                record.getLeadTimeMinutes(),
                record.getCycleTimeMinutes()
        );
    }
}
