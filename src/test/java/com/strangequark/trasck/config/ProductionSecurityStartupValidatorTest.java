package com.strangequark.trasck.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityStartupValidatorTest {

    @Test
    void allowsDevelopmentDefaultsOutsideProductionLikeProfiles() {
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> validator(environment).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentDefaultsInProductionLikeProfiles() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");
        environment.withProperty("trasck.security.jwt-secret", "dev-only-change-me-dev-only-change-me-dev-only-change-me-32");
        environment.withProperty("trasck.secrets.encryption-key", "dev-only-change-me-dev-only-change-me-dev-only-change-me-32");
        environment.withProperty("trasck.security.oauth-assertion-secret", "");
        environment.withProperty("spring.datasource.password", "trasck");
        environment.withProperty("trasck.security.cookie-secure", "false");
        environment.withProperty("cors.allowed-origins", "http://localhost:8080");
        environment.withProperty("trasck.security.oauth-success-redirect", "http://localhost:8080/auth/callback");

        assertThatThrownBy(() -> validator(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe production-like Trasck configuration")
                .hasMessageContaining("trasck.security.jwt-secret")
                .hasMessageContaining("trasck.security.cookie-secure")
                .hasMessageContaining("trasck.security.rate-limit.store")
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void acceptsHardenedProductionLikeConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        environment.withProperty("trasck.security.jwt-secret", "prod-test-jwt-secret-that-is-long-enough-123456");
        environment.withProperty("trasck.secrets.encryption-key", "prod-test-encryption-key-material-123456");
        environment.withProperty("trasck.security.oauth-assertion-secret", "prod-test-oauth-assertion-secret-123456");
        environment.withProperty("spring.datasource.password", "prod-test-database-password-123456");
        environment.withProperty("trasck.security.cookie-secure", "true");
        environment.withProperty("trasck.security.rate-limit.store", "redis");
        environment.withProperty("cors.allowed-origins", "https://app.example.test");
        environment.withProperty("trasck.security.oauth-success-redirect", "https://app.example.test/auth/callback");

        assertThatCode(() -> validator(environment).afterPropertiesSet()).doesNotThrowAnyException();
    }

    private ProductionSecurityStartupValidator validator(MockEnvironment environment) {
        return new ProductionSecurityStartupValidator(environment, new RuntimeSecurityProfile(environment));
    }
}
