package com.strangequark.trasck.integration;

public record ImportTransformPresetCloneRequest(
        String name,
        String description,
        Boolean enabled
) {
}
