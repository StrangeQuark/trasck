package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportConflictBulkResolutionRequest(
        List<UUID> recordIds,
        String resolution,
        String scope,
        String status,
        String conflictStatus,
        String sourceType,
        Integer page,
        Integer pageSize,
        Integer expectedCount,
        String confirmation
) {
}
