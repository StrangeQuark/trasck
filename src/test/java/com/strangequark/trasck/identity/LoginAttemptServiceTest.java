package com.strangequark.trasck.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LoginAttemptServiceTest {

    @Test
    void locksIdentifierAndRemoteAddressAfterTooManyFailures() {
        LoginAttemptService service = new LoginAttemptService(
                2,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC)
        );

        service.recordFailure("admin@example.com", "203.0.113.10");
        assertThatCode(() -> service.assertAllowed("admin@example.com", "203.0.113.10")).doesNotThrowAnyException();

        service.recordFailure("admin@example.com", "203.0.113.10");
        assertThatThrownBy(() -> service.assertAllowed("admin@example.com", "203.0.113.10"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }

    @Test
    void clearsFailuresAfterSuccess() {
        LoginAttemptService service = new LoginAttemptService(
                1,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC)
        );

        service.recordFailure("admin@example.com", "203.0.113.10");
        service.recordSuccess("admin@example.com", "203.0.113.10");

        assertThatCode(() -> service.assertAllowed("admin@example.com", "203.0.113.10")).doesNotThrowAnyException();
    }
}
