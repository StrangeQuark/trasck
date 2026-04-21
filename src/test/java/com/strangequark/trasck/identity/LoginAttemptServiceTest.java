package com.strangequark.trasck.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import com.strangequark.trasck.security.SecurityAuthFailureEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Test
    void canUseRedisBackedCountersForSharedRateLimits() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        SecurityAuthFailureEventRepository failureEventRepository = mock(SecurityAuthFailureEventRepository.class);
        Map<Object, Object> redisState = new HashMap<>();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(redisState);
        doAnswer(invocation -> {
            redisState.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(hashOperations).put(anyString(), any(), any());

        LoginAttemptService service = new LoginAttemptService(
                2,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneOffset.UTC),
                null,
                failureEventRepository,
                "redis",
                redisTemplate
        );

        service.recordFailure("login", "admin@example.com", "203.0.113.10", "auth.login_failed", "Invalid credentials");
        assertThatCode(() -> service.assertAllowed("login", "admin@example.com", "203.0.113.10")).doesNotThrowAnyException();

        service.recordFailure("login", "admin@example.com", "203.0.113.10", "auth.login_failed", "Invalid credentials");

        assertThatThrownBy(() -> service.assertAllowed("login", "admin@example.com", "203.0.113.10"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
        verify(redisTemplate, times(2)).expire(anyString(), any(Duration.class));
        verify(failureEventRepository, times(2)).save(any());
    }
}
