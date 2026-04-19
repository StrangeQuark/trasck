package com.strangequark.trasck.agent;

public record AgentProviderRequest(
        String providerKey,
        String providerType,
        String displayName,
        String dispatchMode,
        String callbackUrl,
        Object capabilitySchema,
        Object config,
        Boolean enabled
) {
}
