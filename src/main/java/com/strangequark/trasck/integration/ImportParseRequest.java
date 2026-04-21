package com.strangequark.trasck.integration;

public record ImportParseRequest(
        String content,
        Object mapping,
        String sourceType,
        String contentType
) {
}
