package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;

public record AuthResponse(
        AuthUserResponse user,
        String tokenType,
        String accessToken,
        OffsetDateTime expiresAt
) {
}
