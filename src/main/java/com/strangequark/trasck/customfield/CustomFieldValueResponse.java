package com.strangequark.trasck.customfield;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomFieldValueResponse(
        UUID id,
        UUID workItemId,
        UUID customFieldId,
        String fieldKey,
        String fieldName,
        String fieldType,
        Object value,
        OffsetDateTime updatedAt
) {
    static CustomFieldValueResponse from(CustomFieldValue value, CustomField field) {
        return new CustomFieldValueResponse(
                value.getId(),
                value.getWorkItemId(),
                value.getCustomFieldId(),
                field == null ? null : field.getKey(),
                field == null ? null : field.getName(),
                field == null ? null : field.getFieldType(),
                JsonValues.toJavaValue(value.getValue()),
                value.getUpdatedAt()
        );
    }
}
