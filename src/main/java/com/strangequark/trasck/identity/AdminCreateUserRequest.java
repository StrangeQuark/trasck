package com.strangequark.trasck.identity;

import java.util.UUID;

public record AdminCreateUserRequest(
        String email,
        String username,
        String displayName,
        String password,
        UUID roleId,
        Boolean emailVerified
) {
}
