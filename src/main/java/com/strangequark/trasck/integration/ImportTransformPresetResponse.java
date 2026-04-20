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
        Integer version,
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
                preset.getVersion(),
                preset.getCreatedAt(),
                preset.getUpdatedAt()
        );
    }
}
