package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportConflictResolutionWorkerResponse(
        UUID workspaceId,
        int processed,
        int completed,
        int failed,
        List<ImportConflictResolutionJobResponse> jobs
) {
}
