package com.strangequark.trasck.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AgentDispatchAttemptRetentionResponse(
        UUID workspaceId,
        UUID agentTaskId,
        UUID providerId,
        UUID agentProfileId,
        UUID workItemId,
        String attemptType,
        String status,
        OffsetDateTime startedFrom,
        OffsetDateTime startedTo,
        OffsetDateTime cutoff,
        long attemptsEligible,
        int attemptsIncluded,
        int attemptsPruned,
        UUID exportJobId,
        UUID fileAttachmentId,
        List<AgentDispatchAttemptResponse> attempts
) {
}
