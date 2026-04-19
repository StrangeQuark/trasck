package com.strangequark.trasck.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkItemAssignmentHistoryResponse(
        UUID id,
        UUID workItemId,
        UUID fromUserId,
        UUID toUserId,
        UUID changedById,
        OffsetDateTime changedAt
) {
    static WorkItemAssignmentHistoryResponse from(WorkItemAssignmentHistory history) {
        return new WorkItemAssignmentHistoryResponse(
                history.getId(),
                history.getWorkItemId(),
                history.getFromUserId(),
                history.getToUserId(),
                history.getChangedById(),
                history.getChangedAt()
        );
    }
}
