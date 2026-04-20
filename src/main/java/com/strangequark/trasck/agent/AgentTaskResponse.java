package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AgentTaskResponse(
        UUID id,
        UUID workspaceId,
        UUID workItemId,
        UUID agentProfileId,
        UUID providerId,
        UUID requestedById,
        String status,
        String dispatchMode,
        String externalTaskId,
        Object contextSnapshot,
        Object requestPayload,
        Object resultPayload,
        OffsetDateTime queuedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        OffsetDateTime canceledAt,
        List<AgentTaskEventResponse> events,
        List<AgentMessageResponse> messages,
        List<AgentArtifactResponse> artifacts,
        List<AgentTaskRepositoryLinkResponse> repositories,
        List<AgentDispatchAttemptResponse> dispatchAttempts,
        String callbackHeaderName,
        String callbackToken
) {
    static AgentTaskResponse from(
            AgentTask task,
            List<AgentTaskEvent> events,
            List<AgentMessage> messages,
            List<AgentArtifact> artifacts,
            List<AgentTaskRepositoryLink> repositories,
            List<AgentDispatchAttempt> dispatchAttempts,
            String callbackToken
    ) {
        return new AgentTaskResponse(
                task.getId(),
                task.getWorkspaceId(),
                task.getWorkItemId(),
                task.getAgentProfileId(),
                task.getProviderId(),
                task.getRequestedById(),
                task.getStatus(),
                task.getDispatchMode(),
                task.getExternalTaskId(),
                JsonValues.toJavaValue(task.getContextSnapshot()),
                JsonValues.toJavaValue(task.getRequestPayload()),
                JsonValues.toJavaValue(task.getResultPayload()),
                task.getQueuedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getFailedAt(),
                task.getCanceledAt(),
                events.stream().map(AgentTaskEventResponse::from).toList(),
                messages.stream().map(AgentMessageResponse::from).toList(),
                artifacts.stream().map(AgentArtifactResponse::from).toList(),
                repositories.stream().map(AgentTaskRepositoryLinkResponse::from).toList(),
                dispatchAttempts.stream().map(AgentDispatchAttemptResponse::from).toList(),
                callbackToken == null ? null : AgentCallbackJwtService.CALLBACK_HEADER,
                callbackToken
        );
    }
}
