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
        ExternalAgentRuntimeConfig.from(objectMapper, providerType(), provider).validate();
    }

    @Override
    public AgentDispatchResult dispatch(AgentTask task, AgentProvider provider, AgentProfile profile) {
        return result(task, provider, profile, "dispatched");
    }

    @Override
    public AgentDispatchResult retry(AgentTask task, AgentProvider provider, AgentProfile profile) {
        return result(task, provider, profile, "retried");
    }

    @Override
    public void cancel(AgentTask task, AgentProvider provider, AgentProfile profile) {
        ExternalAgentRuntimeConfig.from(objectMapper, providerType(), provider).validate();
    }

    private AgentDispatchResult result(AgentTask task, AgentProvider provider, AgentProfile profile, String action) {
        ExternalAgentRuntimeConfig runtime = ExternalAgentRuntimeConfig.from(objectMapper, providerType(), provider);
        runtime.validate();
        ObjectNode payload = runtime.dispatchPayload(task, provider, profile, action);
        return new AgentDispatchResult(runtime.externalTaskId(task), payload);
    }
}
