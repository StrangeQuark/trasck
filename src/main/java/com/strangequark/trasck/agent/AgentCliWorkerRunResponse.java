package com.strangequark.trasck.agent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentCliWorkerRunResponse(
        UUID agentTaskId,
        UUID workspaceId,
        UUID providerId,
        String providerType,
        UUID agentProfileId,
        UUID workItemId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long sizeBytes,
        int fileCount,
        boolean promptPresent,
        boolean taskFilePresent,
        boolean outputPresent
) {
}
