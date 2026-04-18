package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiTokenResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        String tokenType,
        String name,
        String tokenPrefix,
        String token,
        UUID roleId,
        JsonNode scopes,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt
) {
    static ApiTokenResponse from(ApiToken token, String rawToken) {
        return new ApiTokenResponse(
                token.getId(),
                token.getWorkspaceId(),
                token.getUserId(),
                token.getTokenType(),
                token.getName(),
                token.getTokenPrefix(),
                rawToken,
                token.getRoleId(),
                token.getScopes(),
                token.getExpiresAt(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getRevokedAt()
        );
    }
}
