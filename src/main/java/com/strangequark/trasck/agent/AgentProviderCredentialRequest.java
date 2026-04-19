package com.strangequark.trasck.agent;

import java.time.OffsetDateTime;

public record AgentProviderCredentialRequest(
        String credentialType,
        String secret,
        Object metadata,
        OffsetDateTime expiresAt
) {
}
