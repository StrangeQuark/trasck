package com.strangequark.trasck.search;

public record SavedViewRequest(
        String name,
        String viewType,
        Object config,
        String visibility
) {
}
