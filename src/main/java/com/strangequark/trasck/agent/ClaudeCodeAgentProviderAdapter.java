package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class ClaudeCodeAgentProviderAdapter implements AgentProviderAdapter {

    private final ObjectMapper objectMapper;

    public ClaudeCodeAgentProviderAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerType() {
        return "claude_code";
    }

    @Override
    public void validateProvider(AgentProvider provider) {
        if (!providerType().equals(provider.getProviderType())) {
            throw new IllegalArgumentException("Unsupported Claude Code provider type");
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
        // External Claude Code cancellation is provider-specific future work.
    }

    private AgentDispatchResult result(AgentTask task, String action) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("adapter", providerType())
                .put("action", action)
                .put("externalDispatch", false)
                .put("agentTaskId", task.getId().toString());
        return new AgentDispatchResult("claude-code-" + task.getId(), payload);
    }
}
