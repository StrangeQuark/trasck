package com.strangequark.trasck.customfield;

import java.util.UUID;

public record CustomFieldContextRequest(
        UUID projectId,
        UUID workItemTypeId,
        Boolean required,
        Object defaultValue,
        Object validationConfig
) {
}
