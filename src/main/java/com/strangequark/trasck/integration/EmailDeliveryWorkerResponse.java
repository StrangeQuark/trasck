package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record EmailDeliveryWorkerResponse(
        UUID workspaceId,
        int processed,
        int sent,
        int failed,
        int deadLettered,
        List<EmailDeliveryResponse> deliveries
) {
}
