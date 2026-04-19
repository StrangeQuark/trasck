package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class CodexAgentProviderAdapter implements AgentProviderAdapter {

    private final ObjectMapper objectMapper;

    public CodexAgentProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerType() {
        return "codex";
    }

    @Override
    public void validateProvider(AgentProvider provider) {
        if (!providerType().equals(provider.getProviderType())) {
            throw new IllegalArgumentException("Unsupported Codex provider type");
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
        // External Codex cancellation is provider-specific future work.
    }

    private AgentDispatchResult result(AgentTask task, String action) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("adapter", providerType())
                .put("action", action)
                .put("externalDispatch", false)
                .put("agentTaskId", task.getId().toString());
        return new AgentDispatchResult("codex-" + task.getId(), payload);
    }
}
