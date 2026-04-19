package com.strangequark.trasck.search;

import java.util.UUID;

public record SavedViewRequest(
        String name,
        String viewType,
        Object config,
        String visibility,
        UUID projectId,
        UUID teamId
) {
}
