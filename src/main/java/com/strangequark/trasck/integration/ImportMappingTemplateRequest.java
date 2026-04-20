package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportMappingTemplateRequest(
        String name,
        String provider,
        String sourceType,
        String targetType,
        UUID projectId,
        String workItemTypeKey,
        String statusKey,
        Object fieldMapping,
        Object defaults,
        Boolean enabled
) {
}
