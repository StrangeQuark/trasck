package com.strangequark.trasck.customfield;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record FieldConfigurationResponse(
        UUID id,
        UUID workspaceId,
        UUID customFieldId,
        UUID projectId,
        UUID workItemTypeId,
        Boolean required,
        Boolean hidden,
        Object defaultValue,
        Object validationConfig
) {
    static FieldConfigurationResponse from(FieldConfiguration configuration) {
        return new FieldConfigurationResponse(
                configuration.getId(),
                configuration.getWorkspaceId(),
                configuration.getCustomFieldId(),
                configuration.getProjectId(),
                configuration.getWorkItemTypeId(),
                configuration.getRequired(),
                configuration.getHidden(),
                JsonValues.toJavaValue(configuration.getDefaultValue()),
                JsonValues.toJavaValue(configuration.getValidationConfig())
        );
    }
}
