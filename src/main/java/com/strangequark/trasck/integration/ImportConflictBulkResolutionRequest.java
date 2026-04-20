package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportConflictBulkResolutionRequest(
        List<UUID> recordIds,
        String resolution
) {
}
