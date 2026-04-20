package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportTransformPresetRetargetRequest(
        String name,
        String description,
        Boolean enabled,
        List<UUID> mappingTemplateIds
) {
}
