package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportTransformPresetVersionResponse(
        UUID id,
        UUID presetId,
        UUID workspaceId,
        Integer version,
        String name,
        String description,
        Object transformationConfig,
        Boolean enabled,
        String changeType,
        UUID createdById,
        OffsetDateTime createdAt
) {
    static ImportTransformPresetVersionResponse from(ImportTransformPresetVersion version) {
        return new ImportTransformPresetVersionResponse(
                version.getId(),
                version.getPresetId(),
                version.getWorkspaceId(),
                version.getVersion(),
                version.getName(),
                version.getDescription(),
                JsonValues.toJavaValue(version.getTransformationConfig()),
                version.getEnabled(),
                version.getChangeType(),
                version.getCreatedById(),
                version.getCreatedAt()
        );
    }
}
