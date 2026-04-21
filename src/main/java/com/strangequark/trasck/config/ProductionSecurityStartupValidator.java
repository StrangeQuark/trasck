package com.strangequark.trasck.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityStartupValidator implements InitializingBean {

    private static final String DEV_JWT_SECRET = "dev-only-change-me-dev-only-change-me-dev-only-change-me-32";
    private static final List<String> WEAK_DATABASE_PASSWORDS = List.of(
            "trasck",
            "password",
            "postgres",
            "admin",
            "changeme",
            "change-me",
            "dev"
    );

    private final Environment environment;
    private final RuntimeSecurityProfile runtimeSecurityProfile;

    public ProductionSecurityStartupValidator(Environment environment, RuntimeSecurityProfile runtimeSecurityProfile) {
        this.environment = environment;
        this.runtimeSecurityProfile = runtimeSecurityProfile;
    }

    @Override
    public void afterPropertiesSet() {
        if (!runtimeSecurityProfile.isProductionLike()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        validateSecret("trasck.security.jwt-secret", 32, failures);
        validateSecret("trasck.secrets.encryption-key", 16, failures);
        validateSecret("trasck.security.oauth-assertion-secret", 32, failures);
        validateDatabasePassword(failures);
        validateCookieSecure(failures);
        validateCorsOrigins(failures);
        validateOAuthRedirect(failures);
        validateOAuthClientSecrets(failures);
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Unsafe production-like Trasck configuration: " + String.join("; ", failures));
        }
    }

    private void validateSecret(String propertyName, int minLength, List<String> failures) {
        String value = text(propertyName);
        if (value == null || value.length() < minLength || isKnownDevelopmentSecret(value)) {
            failures.add(propertyName + " must be provided with non-development secret material of at least " + minLength + " characters");
        }
    }

    private void validateDatabasePassword(List<String> failures) {
        String password = text("spring.datasource.password");
        if (password == null
                || WEAK_DATABASE_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))
                || password.toLowerCase(Locale.ROOT).contains("replace-with")) {
            failures.add("spring.datasource.password must be set to a non-development database password");
        }
    }

    private void validateCookieSecure(List<String> failures) {
        if (!Boolean.parseBoolean(environment.getProperty("trasck.security.cookie-secure", "false"))) {
            failures.add("trasck.security.cookie-secure must be true");
        }
    }

    private void validateCorsOrigins(List<String> failures) {
        String origins = text("cors.allowed-origins");
        if (origins == null || origins.contains("*") || containsLocalOrigin(origins)) {
            failures.add("cors.allowed-origins must explicitly name non-local production browser origins");
        }
    }

    private void validateOAuthRedirect(List<String> failures) {
        String redirect = text("trasck.security.oauth-success-redirect");
        if (redirect == null || containsLocalOrigin(redirect)) {
            failures.add("trasck.security.oauth-success-redirect must be a non-local production redirect URL");
        }
    }

    private void validateOAuthClientSecrets(List<String> failures) {
        validateOAuthPair("github", failures);
        validateOAuthPair("google", failures);
        validateOAuthPair("gitlab", failures);
        validateOAuthPair("microsoft", failures);
    }

    private void validateOAuthPair(String provider, List<String> failures) {
        String clientId = text("trasck.oauth." + provider + ".client-id");
        String clientSecret = text("trasck.oauth." + provider + ".client-secret");
        boolean clientIdConfigured = clientId != null && !clientId.startsWith("disabled-");
        if (clientIdConfigured && (clientSecret == null || clientSecret.startsWith("disabled-") || isKnownDevelopmentSecret(clientSecret))) {
            failures.add("trasck.oauth." + provider + ".client-secret must be set when the " + provider + " OAuth client is enabled");
        }
    }

    private String text(String propertyName) {
        String value = environment.getProperty(propertyName);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isKnownDevelopmentSecret(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return DEV_JWT_SECRET.equals(value)
                || normalized.contains("dev-only-change-me")
                || normalized.contains("replace-with")
                || normalized.contains("disabled-")
                || normalized.equals("change-me")
                || normalized.equals("changeme")
                || normalized.equals("secret")
                || normalized.equals("password");
    }

    private boolean containsLocalOrigin(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("0.0.0.0")
                || normalized.contains("[::1]");
    }
}
