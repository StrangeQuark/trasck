package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportMappingValueLookupResponse(
        UUID id,
        UUID mappingTemplateId,
        String sourceField,
        String sourceValue,
        String targetField,
        Object targetValue,
        Integer sortOrder,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ImportMappingValueLookupResponse from(ImportMappingValueLookup lookup) {
        return new ImportMappingValueLookupResponse(
                lookup.getId(),
                lookup.getMappingTemplateId(),
                lookup.getSourceField(),
                lookup.getSourceValue(),
                lookup.getTargetField(),
                JsonValues.toJavaValue(lookup.getTargetValue()),
                lookup.getSortOrder(),
                lookup.getEnabled(),
                lookup.getCreatedAt(),
                lookup.getUpdatedAt()
        );
    }
}
