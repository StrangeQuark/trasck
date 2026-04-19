package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkItemEstimateHistoryResponse(
        UUID id,
        UUID workItemId,
        String estimateType,
        BigDecimal oldValue,
        BigDecimal newValue,
        UUID changedById,
        OffsetDateTime changedAt
) {
    static WorkItemEstimateHistoryResponse from(WorkItemEstimateHistory history) {
        return new WorkItemEstimateHistoryResponse(
                history.getId(),
                history.getWorkItemId(),
                history.getEstimateType(),
                history.getOldValue(),
                history.getNewValue(),
                history.getChangedById(),
                history.getChangedAt()
        );
    }
}
