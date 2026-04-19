package com.strangequark.trasck.workitem;

import com.strangequark.trasck.JsonValues;
import com.strangequark.trasck.activity.WorkLog;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkLogResponse(
        UUID id,
        UUID workItemId,
        UUID userId,
        Integer minutesSpent,
        LocalDate workDate,
        OffsetDateTime startedAt,
        String descriptionMarkdown,
        Object descriptionDocument,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static WorkLogResponse from(WorkLog workLog) {
        return new WorkLogResponse(
                workLog.getId(),
                workLog.getWorkItemId(),
                workLog.getUserId(),
                workLog.getMinutesSpent(),
                workLog.getWorkDate(),
                workLog.getStartedAt(),
                workLog.getDescriptionMarkdown(),
                JsonValues.toJavaValue(workLog.getDescriptionDocument()),
                workLog.getCreatedAt(),
                workLog.getUpdatedAt()
        );
    }
}
