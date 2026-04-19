package com.strangequark.trasck.agent;

public interface AgentProviderAdapter {
    String providerType();

    void validateProvider(AgentProvider provider);

    AgentDispatchResult dispatch(AgentTask task, AgentProvider provider, AgentProfile profile);

    AgentDispatchResult retry(AgentTask task, AgentProvider provider, AgentProfile profile);

    void cancel(AgentTask task, AgentProvider provider, AgentProfile profile);
}
