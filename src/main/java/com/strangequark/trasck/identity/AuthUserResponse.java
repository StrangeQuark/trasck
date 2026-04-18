package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String accountType,
        Boolean emailVerified,
        OffsetDateTime lastLoginAt
) {
    static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAccountType(),
                user.getEmailVerified(),
                user.getLastLoginAt()
        );
    }
}
