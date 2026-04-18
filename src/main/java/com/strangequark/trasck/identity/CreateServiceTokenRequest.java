package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateServiceTokenRequest(
        String name,
        String username,
        String displayName,
        UUID roleId,
        List<String> scopes,
        OffsetDateTime expiresAt
) {
}
