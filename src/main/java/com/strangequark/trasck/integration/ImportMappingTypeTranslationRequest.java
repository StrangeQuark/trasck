package com.strangequark.trasck.integration;

public record ImportMappingTypeTranslationRequest(
        String sourceTypeKey,
        String targetTypeKey,
        Boolean enabled
) {
}
