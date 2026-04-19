package com.strangequark.trasck.search;

import java.util.UUID;

public record SavedFilterRequest(
        String name,
        String visibility,
        UUID projectId,
        UUID teamId,
        Object query
) {
}
