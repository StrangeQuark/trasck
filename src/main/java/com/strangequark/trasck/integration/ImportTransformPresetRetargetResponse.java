package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.util.List;
import java.util.UUID;

public record ImportTransformPresetRetargetResponse(
        UUID workspaceId,
        UUID sourcePresetId,
        UUID sourceVersionId,
        Integer sourceVersion,
        ImportTransformPresetResponse clonedPreset,
        String cloneName,
        String cloneDescription,
        Object transformationConfig,
        Boolean enabled,
        List<ImportTransformPresetRetargetTemplateResponse> templates
) {
    static ImportTransformPresetRetargetResponse preview(
            ImportTransformPreset sourcePreset,
            ImportTransformPresetVersion sourceVersion,
            String cloneName,
            String cloneDescription,
            Boolean enabled,
            List<ImportTransformPresetRetargetTemplateResponse> templates
    ) {
        return new ImportTransformPresetRetargetResponse(
                sourcePreset.getWorkspaceId(),
                sourcePreset.getId(),
                sourceVersion.getId(),
                sourceVersion.getVersion(),
                null,
                cloneName,
                cloneDescription,
                JsonValues.toJavaValue(sourceVersion.getTransformationConfig()),
                enabled,
                templates
        );
    }

    static ImportTransformPresetRetargetResponse applied(
            ImportTransformPreset sourcePreset,
            ImportTransformPresetVersion sourceVersion,
            ImportTransformPreset clone,
            List<ImportTransformPresetRetargetTemplateResponse> templates
    ) {
        return new ImportTransformPresetRetargetResponse(
                sourcePreset.getWorkspaceId(),
                sourcePreset.getId(),
                sourceVersion.getId(),
                sourceVersion.getVersion(),
                ImportTransformPresetResponse.from(clone),
                clone.getName(),
                clone.getDescription(),
                JsonValues.toJavaValue(clone.getTransformationConfig()),
                clone.getEnabled(),
                templates
        );
    }
}
