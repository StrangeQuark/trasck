package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;
import java.util.List;

public record CreatePersonalTokenRequest(
        String name,
        List<String> scopes,
        OffsetDateTime expiresAt
) {
}
