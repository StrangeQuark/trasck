package com.strangequark.trasck.integration;

public record ImportSampleResponse(
        String key,
        String label,
        String provider,
        String sourceType,
        String description
) {
}
