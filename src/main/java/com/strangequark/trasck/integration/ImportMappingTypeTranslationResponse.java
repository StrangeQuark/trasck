package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportMappingTypeTranslationResponse(
        UUID id,
        UUID mappingTemplateId,
        String sourceTypeKey,
        String targetTypeKey,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ImportMappingTypeTranslationResponse from(ImportMappingTypeTranslation translation) {
        return new ImportMappingTypeTranslationResponse(
                translation.getId(),
                translation.getMappingTemplateId(),
                translation.getSourceTypeKey(),
                translation.getTargetTypeKey(),
                translation.getEnabled(),
                translation.getCreatedAt(),
                translation.getUpdatedAt()
        );
    }
}
