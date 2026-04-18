package com.strangequark.trasck.identity;

public record OAuthLoginRequest(
        String provider,
        String providerSubject,
        String providerUsername,
        String providerEmail,
        Boolean emailVerified,
        String displayName,
        String avatarUrl,
        Object metadata,
        String assertion
) {
}
