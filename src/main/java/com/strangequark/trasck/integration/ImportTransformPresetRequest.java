package com.strangequark.trasck.integration;

public record ImportTransformPresetRequest(
        String name,
        String description,
        Object transformationConfig,
        Boolean enabled
) {
}
