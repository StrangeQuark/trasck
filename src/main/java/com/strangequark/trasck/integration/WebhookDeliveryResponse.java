package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        UUID webhookId,
        String eventType,
        Object payload,
        String status,
        Integer responseCode,
        String responseBody,
        Integer attemptCount,
        OffsetDateTime nextRetryAt,
        OffsetDateTime createdAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getWebhookId(),
                delivery.getEventType(),
                JsonValues.toJavaValue(delivery.getPayload()),
                delivery.getStatus(),
                delivery.getResponseCode(),
                delivery.getResponseBody(),
                delivery.getAttemptCount(),
                delivery.getNextRetryAt(),
                delivery.getCreatedAt()
        );
    }
}
