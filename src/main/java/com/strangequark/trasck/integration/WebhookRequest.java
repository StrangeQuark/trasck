package com.strangequark.trasck.integration;

public record WebhookRequest(
        String name,
        String url,
        String secret,
        Object eventTypes,
        Boolean enabled
) {
}
