package com.strangequark.trasck.customfield;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record CustomFieldContextResponse(
        UUID id,
        UUID customFieldId,
        UUID projectId,
        UUID workItemTypeId,
        Boolean required,
        Object defaultValue,
        Object validationConfig
) {
    static CustomFieldContextResponse from(CustomFieldContext context) {
        return new CustomFieldContextResponse(
                context.getId(),
                context.getCustomFieldId(),
                context.getProjectId(),
                context.getWorkItemTypeId(),
                context.getRequired(),
                JsonValues.toJavaValue(context.getDefaultValue()),
                JsonValues.toJavaValue(context.getValidationConfig())
        );
    }
}
