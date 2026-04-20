package com.strangequark.trasck.customfield;

import java.util.UUID;

public record FieldConfigurationRequest(
        UUID customFieldId,
        UUID projectId,
        UUID workItemTypeId,
        Boolean required,
        Boolean hidden,
        Object defaultValue,
        Object validationConfig
) {
}
