package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportJobRecordRequest(
        String sourceType,
        String sourceId,
        String targetType,
        UUID targetId,
        Boolean clearTarget,
        String status,
        String errorMessage,
        Object rawPayload
) {
}
