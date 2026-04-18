package com.strangequark.trasck.identity;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
}
