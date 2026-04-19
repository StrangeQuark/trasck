package com.strangequark.trasck.planning;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IterationWorkItemResponse(
        UUID iterationId,
        UUID workItemId,
        UUID addedById,
        OffsetDateTime addedAt,
        OffsetDateTime removedAt
) {
    static IterationWorkItemResponse from(IterationWorkItem item) {
        return new IterationWorkItemResponse(
                item.getId().getIterationId(),
                item.getId().getWorkItemId(),
                item.getAddedById(),
                item.getAddedAt(),
                item.getRemovedAt()
        );
    }
}
