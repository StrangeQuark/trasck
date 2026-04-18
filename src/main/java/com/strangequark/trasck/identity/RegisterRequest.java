package com.strangequark.trasck.identity;

public record RegisterRequest(
        String email,
        String username,
        String displayName,
        String password,
        String invitationToken
) {
}
