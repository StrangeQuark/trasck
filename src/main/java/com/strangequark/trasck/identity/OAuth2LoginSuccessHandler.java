package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuthVerifiedEmailResolver verifiedEmailResolver;
    private final ObjectMapper objectMapper;
    private final boolean cookieSecure;
    private final String successRedirectUri;

    public OAuth2LoginSuccessHandler(
            AuthService authService,
            OAuth2AuthorizedClientService authorizedClientService,
            OAuthVerifiedEmailResolver verifiedEmailResolver,
            ObjectMapper objectMapper,
            @Value("${trasck.security.cookie-secure:false}") boolean cookieSecure,
            @Value("${trasck.security.oauth-success-redirect:http://localhost:5173/auth/callback}") String successRedirectUri
    ) {
        this.authService = authService;
        this.authorizedClientService = authorizedClientService;
        this.verifiedEmailResolver = verifiedEmailResolver;
        this.objectMapper = objectMapper;
        this.cookieSecure = cookieSecure;
        this.successRedirectUri = successRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = oauth.getPrincipal();
        AuthResponse authResponse = authService.oauthLoginFromProvider(toProfile(oauth.getAuthorizedClientRegistrationId(), principal));

        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, authResponse.accessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.between(OffsetDateTime.now(), authResponse.expiresAt()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.sendRedirect(successRedirectUri);
    }

    private OAuthProviderProfile toProfile(String registrationId, OAuth2User principal) {
        Map<String, Object> attributes = principal.getAttributes();
        String provider = registrationId.toLowerCase();
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(registrationId, principal.getName());
        OAuthVerifiedEmailResolver.VerifiedEmail verifiedEmail = verifiedEmailResolver.resolve(provider, attributes, authorizedClient);
        return new OAuthProviderProfile(
                provider,
                firstString(attributes, "sub", "id", "oid", "user_id", "login", "username", "preferred_username"),
                verifiedEmail.email(),
                verifiedEmail.verified(),
                firstString(attributes, "login", "username", "preferred_username", "nickname"),
                firstString(attributes, "name", "displayName"),
                firstString(attributes, "avatar_url", "picture"),
                metadata(attributes)
        );
    }

    private ObjectNode metadata(Map<String, Object> attributes) {
        ObjectNode metadata = objectMapper.createObjectNode();
        attributes.forEach((key, value) -> metadata.set(key, objectMapper.valueToTree(value)));
        return metadata;
    }

    private String firstString(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

}
