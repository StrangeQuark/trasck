package com.strangequark.trasck.search;

import java.util.UUID;

public record RecentItemRequest(
        String entityType,
        UUID entityId
) {
}
