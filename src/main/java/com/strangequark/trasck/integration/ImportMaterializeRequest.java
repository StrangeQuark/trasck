package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportMaterializeRequest(
        UUID mappingTemplateId,
        UUID projectId,
        Integer limit,
        Boolean updateExisting
) {
}
