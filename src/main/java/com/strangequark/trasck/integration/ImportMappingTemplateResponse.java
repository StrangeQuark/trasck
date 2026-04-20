package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportMappingTemplateResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String name,
        String provider,
        String sourceType,
        String targetType,
        String workItemTypeKey,
        String statusKey,
        UUID transformPresetId,
        Object fieldMapping,
        Object defaults,
        Object transformationConfig,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ImportMappingTemplateResponse from(ImportMappingTemplate template) {
        return new ImportMappingTemplateResponse(
                template.getId(),
                template.getWorkspaceId(),
                template.getProjectId(),
                template.getName(),
                template.getProvider(),
                template.getSourceType(),
                template.getTargetType(),
                template.getWorkItemTypeKey(),
                template.getStatusKey(),
                template.getTransformPresetId(),
                JsonValues.toJavaValue(template.getFieldMapping()),
                JsonValues.toJavaValue(template.getDefaults()),
                JsonValues.toJavaValue(template.getTransformationConfig()),
                template.getEnabled(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
