package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record CreatePersonalTokenRequest(
        String name,
        JsonNode scopes,
        OffsetDateTime expiresAt
) {
}
