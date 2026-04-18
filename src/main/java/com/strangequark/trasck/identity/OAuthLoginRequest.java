package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;

public record OAuthLoginRequest(
        String provider,
        String providerSubject,
        String providerUsername,
        String providerEmail,
        Boolean emailVerified,
        String displayName,
        String avatarUrl,
        JsonNode metadata,
        String assertion
) {
}
