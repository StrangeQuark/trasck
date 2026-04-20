package com.strangequark.trasck.integration;

public record ImportMappingStatusTranslationRequest(
        String sourceStatusKey,
        String targetStatusKey,
        Boolean enabled
) {
}
