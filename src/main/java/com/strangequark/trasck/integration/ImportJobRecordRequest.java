package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportJobRecordRequest(
        String sourceType,
        String sourceId,
        String targetType,
        UUID targetId,
        String status,
        String errorMessage,
        Object rawPayload
) {
}
