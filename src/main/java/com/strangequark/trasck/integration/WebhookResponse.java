package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WebhookResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String url,
        Boolean secretConfigured,
        String secretKeyId,
        String previousSecretKeyId,
        OffsetDateTime secretRotatedAt,
        OffsetDateTime previousSecretExpiresAt,
        Object eventTypes,
        Boolean enabled
) {
    public static WebhookResponse from(Webhook webhook) {
        return new WebhookResponse(
                webhook.getId(),
                webhook.getWorkspaceId(),
                webhook.getName(),
                webhook.getUrl(),
                (webhook.getSecretHash() != null && !webhook.getSecretHash().isBlank())
                        || (webhook.getSecretEncrypted() != null && !webhook.getSecretEncrypted().isBlank()),
                webhook.getSecretKeyId(),
                webhook.getPreviousSecretKeyId(),
                webhook.getSecretRotatedAt(),
                webhook.getPreviousSecretExpiresAt(),
                JsonValues.toJavaValue(webhook.getEventTypes()),
                webhook.getEnabled()
        );
    }
}
