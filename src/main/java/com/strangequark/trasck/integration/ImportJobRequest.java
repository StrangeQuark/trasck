package com.strangequark.trasck.integration;

public record ImportJobRequest(
        String provider,
        Object config
) {
}
