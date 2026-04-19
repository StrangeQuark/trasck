package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentWorkerTaskResponse(
        String protocolVersion,
        List<String> transportModes,
        String transport,
        UUID workspaceId,
        UUID providerId,
        String providerKey,
        UUID taskId,
        UUID workItemId,
        UUID agentProfileId,
        String status,
        String dispatchMode,
        String externalTaskId,
        Object contextSnapshot,
        Object requestPayload,
        List<AgentTaskRepositoryLinkResponse> repositories,
        Map<String, String> endpoints,
        String callbackHeaderName,
        String callbackToken
) {
    static AgentWorkerTaskResponse from(
            AgentTask task,
            AgentProvider provider,
            List<AgentTaskRepositoryLink> repositories,
            String transport,
            Map<String, String> endpoints,
            String callbackToken
    ) {
        return new AgentWorkerTaskResponse(
                "trasck.worker.v1",
                List.of("polling", "webhook_push"),
                transport,
                task.getWorkspaceId(),
                provider.getId(),
                provider.getProviderKey(),
                task.getId(),
                task.getWorkItemId(),
                task.getAgentProfileId(),
                task.getStatus(),
                task.getDispatchMode(),
                task.getExternalTaskId(),
                JsonValues.toJavaValue(task.getContextSnapshot()),
                JsonValues.toJavaValue(task.getRequestPayload()),
                repositories.stream().map(AgentTaskRepositoryLinkResponse::from).toList(),
                endpoints,
                AgentCallbackJwtService.CALLBACK_HEADER,
                callbackToken
        );
    }
}
