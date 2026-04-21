package com.strangequark.trasck.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginAttemptService {

    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration lockoutDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${trasck.security.login.max-failures:5}") int maxFailures,
            @Value("${trasck.security.login.failure-window:PT15M}") String failureWindow,
            @Value("${trasck.security.login.lockout-duration:PT15M}") String lockoutDuration
    ) {
        this(maxFailures, Duration.parse(failureWindow), Duration.parse(lockoutDuration), Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration failureWindow, Duration lockoutDuration, Clock clock) {
        this.maxFailures = Math.max(1, maxFailures);
        this.failureWindow = failureWindow;
        this.lockoutDuration = lockoutDuration;
        this.clock = clock;
    }

    public void assertAllowed(String identifier, String remoteAddress) {
        AttemptState state = attempts.get(key(identifier, remoteAddress));
        if (state != null && state.lockedUntil != null && state.lockedUntil.isAfter(now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts; try again later");
        }
    }

    public void recordFailure(String identifier, String remoteAddress) {
        attempts.compute(key(identifier, remoteAddress), (ignored, existing) -> {
            Instant current = now();
            AttemptState state = existing == null || existing.firstFailureAt.plus(failureWindow).isBefore(current)
                    ? new AttemptState(current, 0, null)
                    : existing;
            int failures = state.failures + 1;
            Instant lockedUntil = failures >= maxFailures ? current.plus(lockoutDuration) : state.lockedUntil;
            return new AttemptState(state.firstFailureAt, failures, lockedUntil);
        });
    }

    public void recordSuccess(String identifier, String remoteAddress) {
        attempts.remove(key(identifier, remoteAddress));
    }

    private Instant now() {
        return clock.instant();
    }

    private String key(String identifier, String remoteAddress) {
        return (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT))
                + "|"
                + (remoteAddress == null ? "" : remoteAddress.trim().toLowerCase(Locale.ROOT));
    }

    private record AttemptState(Instant firstFailureAt, int failures, Instant lockedUntil) {
    }
}
