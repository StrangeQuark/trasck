package com.strangequark.trasck.agent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentDispatchAttemptExportRequest(
        UUID agentTaskId,
        UUID providerId,
        UUID agentProfileId,
        UUID workItemId,
        String attemptType,
        String status,
        OffsetDateTime startedFrom,
        OffsetDateTime startedTo,
        Integer limit
) {
}
