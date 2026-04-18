package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;

public record OAuthProviderProfile(
        String provider,
        String providerSubject,
        String providerEmail,
        Boolean emailVerified,
        String providerUsername,
        String displayName,
        String avatarUrl,
        JsonNode metadata
) {
}
