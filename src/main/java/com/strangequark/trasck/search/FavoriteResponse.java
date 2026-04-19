package com.strangequark.trasck.search;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FavoriteResponse(
        UUID id,
        UUID userId,
        String entityType,
        UUID entityId,
        OffsetDateTime createdAt
) {
    static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getUserId(),
                favorite.getEntityType(),
                favorite.getEntityId(),
                favorite.getCreatedAt()
        );
    }
}
