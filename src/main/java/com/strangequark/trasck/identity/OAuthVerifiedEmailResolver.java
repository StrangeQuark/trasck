package com.strangequark.trasck.identity;

import com.strangequark.trasck.security.OutboundUrlPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OAuthVerifiedEmailResolver {

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String GITLAB_EMAILS_URL = "https://gitlab.com/api/v4/user/emails";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final OutboundUrlPolicy outboundUrlPolicy;

    public OAuthVerifiedEmailResolver(OutboundUrlPolicy outboundUrlPolicy) {
        this.outboundUrlPolicy = outboundUrlPolicy;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public VerifiedEmail resolve(String provider, Map<String, Object> attributes, OAuth2AuthorizedClient authorizedClient) {
        String normalizedProvider = provider.toLowerCase();
        String attributeEmail = firstString(attributes, "email", "mail", "preferred_username", "userPrincipalName");
        return switch (normalizedProvider) {
            case "google" -> new VerifiedEmail(attributeEmail, Boolean.TRUE.equals(booleanValue(attributes, "email_verified")));
            case "github" -> githubVerifiedEmail(attributeEmail, attributes, authorizedClient);
            case "gitlab" -> gitlabVerifiedEmail(attributeEmail, attributes, authorizedClient);
            case "microsoft" -> microsoftVerifiedEmail(attributeEmail, attributes);
            default -> new VerifiedEmail(attributeEmail, false);
        };
    }

    private VerifiedEmail githubVerifiedEmail(String attributeEmail, Map<String, Object> attributes, OAuth2AuthorizedClient authorizedClient) {
        Boolean verifiedAttribute = booleanValue(attributes, "verified_email");
        if (Boolean.TRUE.equals(verifiedAttribute) && hasText(attributeEmail)) {
            return new VerifiedEmail(attributeEmail, true);
        }
        if (authorizedClient == null) {
            return new VerifiedEmail(attributeEmail, false);
        }
        try {
            outboundUrlPolicy.validateResolvedHttpUri(URI.create(GITHUB_EMAILS_URL), "GitHub verified-email URL");
            List<Map<String, Object>> emails = restClient.get()
                    .uri(GITHUB_EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorizedClient.getAccessToken().getTokenValue())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            if (emails == null) {
                return new VerifiedEmail(attributeEmail, false);
            }
            return emails.stream()
                    .filter(email -> Boolean.TRUE.equals(booleanValue(email, "verified")))
                    .filter(email -> attributeEmail == null || attributeEmail.equalsIgnoreCase(firstString(email, "email")))
                    .findFirst()
                    .map(email -> new VerifiedEmail(firstString(email, "email"), true))
                    .orElse(new VerifiedEmail(attributeEmail, false));
        } catch (RuntimeException ex) {
            return new VerifiedEmail(attributeEmail, false);
        }
    }

    private VerifiedEmail gitlabVerifiedEmail(String attributeEmail, Map<String, Object> attributes, OAuth2AuthorizedClient authorizedClient) {
        if (hasText(attributeEmail) && hasText(firstString(attributes, "confirmed_at"))) {
            return new VerifiedEmail(attributeEmail, true);
        }
        if (authorizedClient == null) {
            return new VerifiedEmail(attributeEmail, false);
        }
        try {
            outboundUrlPolicy.validateResolvedHttpUri(URI.create(GITLAB_EMAILS_URL), "GitLab verified-email URL");
            List<Map<String, Object>> emails = restClient.get()
                    .uri(GITLAB_EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authorizedClient.getAccessToken().getTokenValue())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            if (emails == null) {
                return new VerifiedEmail(attributeEmail, false);
            }
            return emails.stream()
                    .filter(email -> hasText(firstString(email, "confirmed_at")))
                    .filter(email -> attributeEmail == null || attributeEmail.equalsIgnoreCase(firstString(email, "email")))
                    .findFirst()
                    .map(email -> new VerifiedEmail(firstString(email, "email"), true))
                    .orElse(new VerifiedEmail(attributeEmail, false));
        } catch (RuntimeException ex) {
            return new VerifiedEmail(attributeEmail, false);
        }
    }

    private VerifiedEmail microsoftVerifiedEmail(String attributeEmail, Map<String, Object> attributes) {
        Boolean verified = booleanValue(attributes, "email_verified");
        return new VerifiedEmail(attributeEmail, Boolean.TRUE.equals(verified));
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

    private Boolean booleanValue(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record VerifiedEmail(String email, boolean verified) {
    }
}
