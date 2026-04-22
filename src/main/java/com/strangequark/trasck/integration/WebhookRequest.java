package com.strangequark.trasck.integration;

public record WebhookRequest(
        String name,
        String url,
        String secret,
        Long previousSecretOverlapSeconds,
        Object eventTypes,
        Boolean enabled
) {
}
