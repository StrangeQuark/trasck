package com.strangequark.trasck.agent;

public record AgentProviderCredentialRequest(
        String credentialType,
        String secret,
        Object metadata
) {
}
