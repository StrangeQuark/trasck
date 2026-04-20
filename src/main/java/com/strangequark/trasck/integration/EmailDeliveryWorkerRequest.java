package com.strangequark.trasck.integration;

public record EmailDeliveryWorkerRequest(
        Integer limit,
        Integer maxAttempts,
        Boolean dryRun
) {
}
