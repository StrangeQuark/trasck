package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportTransformPresetRetargetTemplateResponse(
        UUID id,
        String name,
        String provider,
        UUID currentTransformPresetId,
        UUID newTransformPresetId,
        Boolean willRetarget,
        String reason
) {
    static ImportTransformPresetRetargetTemplateResponse from(
            ImportMappingTemplate template,
            UUID newTransformPresetId,
            boolean willRetarget,
            String reason
    ) {
        return new ImportTransformPresetRetargetTemplateResponse(
                template.getId(),
                template.getName(),
                template.getProvider(),
                template.getTransformPresetId(),
                newTransformPresetId,
                willRetarget,
                reason
        );
    }
}
