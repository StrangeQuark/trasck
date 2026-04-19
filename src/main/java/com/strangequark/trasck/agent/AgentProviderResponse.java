package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentProviderResponse(
        UUID id,
        UUID workspaceId,
        String providerKey,
        String providerType,
        String displayName,
        String dispatchMode,
        String callbackUrl,
        Object capabilitySchema,
        Object config,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static AgentProviderResponse from(AgentProvider provider) {
        return new AgentProviderResponse(
                provider.getId(),
                provider.getWorkspaceId(),
                provider.getProviderKey(),
                provider.getProviderType(),
                provider.getDisplayName(),
                provider.getDispatchMode(),
                provider.getCallbackUrl(),
                JsonValues.toJavaValue(provider.getCapabilitySchema()),
                JsonValues.toJavaValue(provider.getConfig()),
                provider.getEnabled(),
                provider.getCreatedAt(),
                provider.getUpdatedAt()
        );
    }
}
