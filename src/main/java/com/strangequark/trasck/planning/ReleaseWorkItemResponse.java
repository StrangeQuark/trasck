package com.strangequark.trasck.planning;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReleaseWorkItemResponse(
        UUID releaseId,
        UUID workItemId,
        UUID addedById,
        OffsetDateTime addedAt
) {
    static ReleaseWorkItemResponse from(ReleaseWorkItem item) {
        return new ReleaseWorkItemResponse(
                item.getId().getReleaseId(),
                item.getId().getWorkItemId(),
                item.getAddedById(),
                item.getAddedAt()
        );
    }
}
