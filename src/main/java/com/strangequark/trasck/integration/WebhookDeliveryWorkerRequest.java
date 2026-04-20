package com.strangequark.trasck.integration;

public record WebhookDeliveryWorkerRequest(
        Integer limit,
        Integer maxAttempts,
        Boolean dryRun
) {
}
