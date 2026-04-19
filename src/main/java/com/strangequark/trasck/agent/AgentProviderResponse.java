package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
                JsonValues.toJavaValue(redact(provider.getConfig())),
                provider.getEnabled(),
                provider.getCreatedAt(),
                provider.getUpdatedAt()
        );
    }

    private static JsonNode redact(JsonNode value) {
        if (value == null || value.isNull()) {
            return value;
        }
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            for (var field : value.properties()) {
                if ("privateKeyPem".equals(field.getKey())) {
                    result.put(field.getKey(), "[REDACTED]");
                } else {
                    result.set(field.getKey(), redact(field.getValue()));
                }
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(redact(item)));
            return result;
        }
        return value.deepCopy();
    }
}
