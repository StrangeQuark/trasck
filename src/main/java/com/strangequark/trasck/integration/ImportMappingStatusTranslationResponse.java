package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportMappingStatusTranslationResponse(
        UUID id,
        UUID mappingTemplateId,
        String sourceStatusKey,
        String targetStatusKey,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ImportMappingStatusTranslationResponse from(ImportMappingStatusTranslation translation) {
        return new ImportMappingStatusTranslationResponse(
                translation.getId(),
                translation.getMappingTemplateId(),
                translation.getSourceStatusKey(),
                translation.getTargetStatusKey(),
                translation.getEnabled(),
                translation.getCreatedAt(),
                translation.getUpdatedAt()
        );
    }
}
