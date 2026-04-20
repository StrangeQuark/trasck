package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class SimulatedAgentProviderAdapter implements AgentProviderAdapter {

    private final ObjectMapper objectMapper;

    public SimulatedAgentProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerType() {
        return "simulated";
    }

    @Override
    public void validateProvider(AgentProvider provider) {
        if (!providerType().equals(provider.getProviderType())) {
            throw new IllegalArgumentException("Unsupported simulated provider type");
        }
    }

    @Override
    public AgentDispatchResult dispatch(AgentTask task, AgentProvider provider, AgentProfile profile) {
        return result(task, "dispatched");
    }

    @Override
    public AgentDispatchResult retry(AgentTask task, AgentProvider provider, AgentProfile profile) {
        return result(task, "retried");
    }

    @Override
    public void cancel(AgentTask task, AgentProvider provider, AgentProfile profile) {
        // The simulated adapter has no external process to stop.
    }

    private AgentDispatchResult result(AgentTask task, String action) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("adapter", providerType())
                .put("protocolVersion", "trasck.agent-runtime.v1")
                .put("action", action)
                .put("providerRuntime", "stub")
                .put("transport", "internal_stub")
                .put("externalDispatch", false)
                .put("callbackHeaderName", AgentCallbackJwtService.CALLBACK_HEADER)
                .put("requiresCallbackJwt", true)
                .put("retrySupported", true)
                .put("cancelSupported", true)
                .put("requestChangesSupported", true)
                .put("artifactCallbackSupported", true)
                .put("idempotencyKey", providerType() + ":" + task.getId() + ":" + action)
                .put("agentTaskId", task.getId().toString());
        return new AgentDispatchResult("simulated-" + task.getId(), payload);
    }
}
