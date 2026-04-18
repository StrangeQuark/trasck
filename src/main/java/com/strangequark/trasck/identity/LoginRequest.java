package com.strangequark.trasck.identity;

public record LoginRequest(
        String identifier,
        String password
) {
}
