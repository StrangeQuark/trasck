package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class GenericWorkerAgentProviderAdapter implements AgentProviderAdapter {

    private final ObjectMapper objectMapper;

    public GenericWorkerAgentProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerType() {
        return "generic_worker";
    }

    @Override
    public void validateProvider(AgentProvider provider) {
        if (!providerType().equals(provider.getProviderType())) {
            throw new IllegalArgumentException("Unsupported generic worker provider type");
        }
    }

    @Override
    public AgentDispatchResult dispatch(AgentTask task, AgentProvider provider, AgentProfile profile) {
        return result(task, provider, "dispatched");
    }

    @Override
    public AgentDispatchResult retry(AgentTask task, AgentProvider provider, AgentProfile profile) {
        return result(task, provider, "retried");
    }

    @Override
    public void cancel(AgentTask task, AgentProvider provider, AgentProfile profile) {
        // Worker cancellation will be handled by the eventual worker transport.
    }

    private AgentDispatchResult result(AgentTask task, AgentProvider provider, String action) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("adapter", providerType())
                .put("protocolVersion", "trasck.worker.v1")
                .put("action", action)
                .put("dispatchMode", provider.getDispatchMode())
                .put("callbackHeaderName", AgentCallbackJwtService.CALLBACK_HEADER)
                .put("externalDispatch", false)
                .put("pollingSupported", true)
                .put("webhookPushSupported", true)
                .put("agentTaskId", task.getId().toString());
        if (provider.getCallbackUrl() != null && !provider.getCallbackUrl().isBlank()) {
            payload.put("pushTargetUrl", provider.getCallbackUrl());
        }
        return new AgentDispatchResult("worker-" + task.getId(), payload);
    }
}
