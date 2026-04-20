package com.strangequark.trasck.agent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentDispatchAttemptRetentionRequest(
        UUID agentTaskId,
        UUID providerId,
        UUID agentProfileId,
        UUID workItemId,
        String attemptType,
        String status,
        OffsetDateTime startedFrom,
        OffsetDateTime startedTo,
        Integer retentionDays,
        Boolean exportBeforePrune
) {
}
