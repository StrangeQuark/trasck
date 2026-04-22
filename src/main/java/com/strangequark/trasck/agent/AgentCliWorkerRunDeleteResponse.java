package com.strangequark.trasck.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AgentCliWorkerRunDeleteResponse(
        UUID workspaceId,
        OffsetDateTime cutoff,
        int deletedRuns,
        long deletedBytes,
        List<UUID> deletedAgentTaskIds
) {
}
