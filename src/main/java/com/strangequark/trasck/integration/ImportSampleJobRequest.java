package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportSampleJobRequest(
        UUID projectId,
        Boolean createMappingTemplate
) {
}
