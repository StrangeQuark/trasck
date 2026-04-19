package com.strangequark.trasck.search;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecentItemResponse(
        UUID id,
        UUID userId,
        String entityType,
        UUID entityId,
        OffsetDateTime viewedAt
) {
    static RecentItemResponse from(RecentItem recentItem) {
        return new RecentItemResponse(
                recentItem.getId(),
                recentItem.getUserId(),
                recentItem.getEntityType(),
                recentItem.getEntityId(),
                recentItem.getViewedAt()
        );
    }
}
