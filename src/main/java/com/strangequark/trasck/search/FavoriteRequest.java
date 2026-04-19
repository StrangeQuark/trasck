package com.strangequark.trasck.search;

import java.util.UUID;

public record FavoriteRequest(
        String entityType,
        UUID entityId
) {
}
