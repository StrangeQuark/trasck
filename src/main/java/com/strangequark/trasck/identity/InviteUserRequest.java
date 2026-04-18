package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InviteUserRequest(
        String email,
        UUID roleId,
        UUID projectId,
        UUID projectRoleId,
        OffsetDateTime expiresAt
) {
}
