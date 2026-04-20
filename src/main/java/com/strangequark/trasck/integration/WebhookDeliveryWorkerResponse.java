package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record WebhookDeliveryWorkerResponse(
        UUID workspaceId,
        int processed,
        int delivered,
        int failed,
        int deadLettered,
        List<WebhookDeliveryResponse> deliveries
) {
}
