package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportTransformPresetResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        Object transformationConfig,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ImportTransformPresetResponse from(ImportTransformPreset preset) {
        return new ImportTransformPresetResponse(
                preset.getId(),
                preset.getWorkspaceId(),
                preset.getName(),
                preset.getDescription(),
                JsonValues.toJavaValue(preset.getTransformationConfig()),
                preset.getEnabled(),
                preset.getCreatedAt(),
                preset.getUpdatedAt()
        );
    }
}
