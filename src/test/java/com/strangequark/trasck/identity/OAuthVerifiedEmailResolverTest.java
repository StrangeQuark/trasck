package com.strangequark.trasck.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.strangequark.trasck.security.OutboundUrlPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuthVerifiedEmailResolverTest {

    private final OAuthVerifiedEmailResolver resolver = new OAuthVerifiedEmailResolver(new OutboundUrlPolicy(""));

    @Test
    void usesProviderSpecificVerifiedEmailRules() {
        OAuthVerifiedEmailResolver.VerifiedEmail google = resolver.resolve(
                "google",
                Map.of("email", "verified@example.com", "email_verified", true),
                null
        );
        assertThat(google.email()).isEqualTo("verified@example.com");
        assertThat(google.verified()).isTrue();

        OAuthVerifiedEmailResolver.VerifiedEmail githubWithoutVerifiedEmail = resolver.resolve(
                "github",
                Map.of("email", "unverified@example.com"),
                null
        );
        assertThat(githubWithoutVerifiedEmail.email()).isEqualTo("unverified@example.com");
        assertThat(githubWithoutVerifiedEmail.verified()).isFalse();

        OAuthVerifiedEmailResolver.VerifiedEmail microsoftWithoutExplicitVerification = resolver.resolve(
                "microsoft",
                Map.of("preferred_username", "user@example.com"),
                null
        );
        assertThat(microsoftWithoutExplicitVerification.email()).isEqualTo("user@example.com");
        assertThat(microsoftWithoutExplicitVerification.verified()).isFalse();
    }
}
