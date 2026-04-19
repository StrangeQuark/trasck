package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentProviderCredentialResponse(
        UUID id,
        UUID providerId,
        String credentialType,
        Object metadata,
        Boolean active,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime rotatedAt
) {
    static AgentProviderCredentialResponse from(AgentProviderCredential credential) {
        return new AgentProviderCredentialResponse(
                credential.getId(),
                credential.getProviderId(),
                credential.getCredentialType(),
                JsonValues.toJavaValue(credential.getMetadata()),
                credential.getActive(),
                credential.getExpiresAt(),
                credential.getCreatedAt(),
                credential.getRotatedAt()
        );
    }
}
