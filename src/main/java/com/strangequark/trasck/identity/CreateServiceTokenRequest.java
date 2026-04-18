package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateServiceTokenRequest(
        String name,
        String username,
        String displayName,
        UUID roleId,
        JsonNode scopes,
        OffsetDateTime expiresAt
) {
}
