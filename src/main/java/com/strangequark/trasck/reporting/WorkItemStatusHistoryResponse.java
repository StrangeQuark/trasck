package com.strangequark.trasck.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkItemStatusHistoryResponse(
        UUID id,
        UUID workItemId,
        UUID fromStatusId,
        UUID toStatusId,
        UUID changedById,
        OffsetDateTime changedAt
) {
    static WorkItemStatusHistoryResponse from(WorkItemStatusHistory history) {
        return new WorkItemStatusHistoryResponse(
                history.getId(),
                history.getWorkItemId(),
                history.getFromStatusId(),
                history.getToStatusId(),
                history.getChangedById(),
                history.getChangedAt()
        );
    }
}
