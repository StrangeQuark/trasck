package com.strangequark.trasck.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
public class OAuthClientConfig {

    private static final String REDIRECT_URI = "{baseUrl}/api/v1/auth/oauth2/callback/{registrationId}";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${trasck.oauth.github.client-id:disabled-github-client-id}") String githubClientId,
            @Value("${trasck.oauth.github.client-secret:disabled-github-client-secret}") String githubClientSecret,
            @Value("${trasck.oauth.google.client-id:disabled-google-client-id}") String googleClientId,
            @Value("${trasck.oauth.google.client-secret:disabled-google-client-secret}") String googleClientSecret,
            @Value("${trasck.oauth.gitlab.client-id:disabled-gitlab-client-id}") String gitlabClientId,
            @Value("${trasck.oauth.gitlab.client-secret:disabled-gitlab-client-secret}") String gitlabClientSecret,
            @Value("${trasck.oauth.microsoft.client-id:disabled-microsoft-client-id}") String microsoftClientId,
            @Value("${trasck.oauth.microsoft.client-secret:disabled-microsoft-client-secret}") String microsoftClientSecret
    ) {
        return new InMemoryClientRegistrationRepository(List.of(
                github(githubClientId, githubClientSecret),
                google(googleClientId, googleClientSecret),
                gitlab(gitlabClientId, gitlabClientSecret),
                microsoft(microsoftClientId, microsoftClientSecret)
        ));
    }

    private ClientRegistration github(String clientId, String clientSecret) {
        return CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private ClientRegistration google(String clientId, String clientSecret) {
        return CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private ClientRegistration gitlab(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("gitlab")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("read_user", "email")
                .authorizationUri("https://gitlab.com/oauth/authorize")
                .tokenUri("https://gitlab.com/oauth/token")
                .userInfoUri("https://gitlab.com/api/v4/user")
                .userNameAttributeName("id")
                .clientName("GitLab")
                .build();
    }

    private ClientRegistration microsoft(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("microsoft")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("openid", "profile", "email")
                .authorizationUri("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .jwkSetUri("https://login.microsoftonline.com/common/discovery/v2.0/keys")
                .userInfoUri("https://graph.microsoft.com/oidc/userinfo")
                .userNameAttributeName("sub")
                .clientName("Microsoft")
                .build();
    }
}
