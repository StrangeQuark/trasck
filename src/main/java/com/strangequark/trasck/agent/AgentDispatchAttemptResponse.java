package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentDispatchAttemptResponse(
        UUID id,
        UUID workspaceId,
        UUID agentTaskId,
        UUID providerId,
        UUID agentProfileId,
        UUID workItemId,
        UUID requestedById,
        String attemptType,
        String dispatchMode,
        String providerType,
        String transport,
        String status,
        String externalTaskId,
        String idempotencyKey,
        Boolean externalDispatch,
        Object requestPayload,
        Object responsePayload,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    static AgentDispatchAttemptResponse from(AgentDispatchAttempt attempt) {
        return new AgentDispatchAttemptResponse(
                attempt.getId(),
                attempt.getWorkspaceId(),
                attempt.getAgentTaskId(),
                attempt.getProviderId(),
                attempt.getAgentProfileId(),
                attempt.getWorkItemId(),
                attempt.getRequestedById(),
                attempt.getAttemptType(),
                attempt.getDispatchMode(),
                attempt.getProviderType(),
                attempt.getTransport(),
                attempt.getStatus(),
                attempt.getExternalTaskId(),
                attempt.getIdempotencyKey(),
                attempt.getExternalDispatch(),
                JsonValues.toJavaValue(attempt.getRequestPayload()),
                JsonValues.toJavaValue(attempt.getResponsePayload()),
                attempt.getErrorMessage(),
                attempt.getStartedAt(),
                attempt.getFinishedAt()
        );
    }
}
