package com.strangequark.trasck.customfield;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomFieldResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String key,
        String fieldType,
        Object options,
        Boolean searchable,
        Boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
    static CustomFieldResponse from(CustomField field) {
        return new CustomFieldResponse(
                field.getId(),
                field.getWorkspaceId(),
                field.getName(),
                field.getKey(),
                field.getFieldType(),
                JsonValues.toJavaValue(field.getOptions()),
                field.getSearchable(),
                field.getArchived(),
                field.getCreatedAt(),
                field.getUpdatedAt(),
                field.getVersion()
        );
    }
}
