package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmailDeliveryResponse(
        UUID id,
        UUID workspaceId,
        UUID automationJobId,
        UUID actionId,
        String provider,
        String fromEmail,
        String recipientEmail,
        String subject,
        String body,
        String status,
        Integer attemptCount,
        String responseBody,
        OffsetDateTime nextRetryAt,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt
) {
    public static EmailDeliveryResponse from(EmailDelivery delivery) {
        return new EmailDeliveryResponse(
                delivery.getId(),
                delivery.getWorkspaceId(),
                delivery.getAutomationJobId(),
                delivery.getActionId(),
                delivery.getProvider(),
                delivery.getFromEmail(),
                delivery.getRecipientEmail(),
                delivery.getSubject(),
                delivery.getBody(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getResponseBody(),
                delivery.getNextRetryAt(),
                delivery.getCreatedAt(),
                delivery.getSentAt()
        );
    }
}
