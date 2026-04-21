package com.strangequark.trasck.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.strangequark.trasck.security.SecurityAuthFailureEvent;
import com.strangequark.trasck.security.SecurityAuthFailureEventRepository;
import com.strangequark.trasck.security.SecurityRateLimitAttempt;
import com.strangequark.trasck.security.SecurityRateLimitAttemptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginAttemptService {

    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration failureWindow;
    private final Duration lockoutDuration;
    private final Clock clock;
    private final SecurityRateLimitAttemptRepository attemptRepository;
    private final SecurityAuthFailureEventRepository failureEventRepository;
    private final String rateLimitStore;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public LoginAttemptService(
            @Value("${trasck.security.login.max-failures:5}") int maxFailures,
            @Value("${trasck.security.login.failure-window:PT15M}") String failureWindow,
            @Value("${trasck.security.login.lockout-duration:PT15M}") String lockoutDuration,
            @Value("${trasck.security.rate-limit.store:database}") String rateLimitStore,
            SecurityRateLimitAttemptRepository attemptRepository,
            SecurityAuthFailureEventRepository failureEventRepository,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        this(
                maxFailures,
                Duration.parse(failureWindow),
                Duration.parse(lockoutDuration),
                Clock.systemUTC(),
                attemptRepository,
                failureEventRepository,
                rateLimitStore,
                redisTemplateProvider.getIfAvailable()
        );
    }

    LoginAttemptService(int maxFailures, Duration failureWindow, Duration lockoutDuration, Clock clock) {
        this(maxFailures, failureWindow, lockoutDuration, clock, null, null, "memory", null);
    }

    LoginAttemptService(
            int maxFailures,
            Duration failureWindow,
            Duration lockoutDuration,
            Clock clock,
            SecurityRateLimitAttemptRepository attemptRepository,
            SecurityAuthFailureEventRepository failureEventRepository,
            String rateLimitStore,
            StringRedisTemplate redisTemplate
    ) {
        this.maxFailures = Math.max(1, maxFailures);
        this.failureWindow = failureWindow;
        this.lockoutDuration = lockoutDuration;
        this.clock = clock;
        this.attemptRepository = attemptRepository;
        this.failureEventRepository = failureEventRepository;
        this.rateLimitStore = normalized(rateLimitStore == null ? "database" : rateLimitStore);
        this.redisTemplate = redisTemplate;
        validateStore();
    }


    @Transactional(readOnly = true)
    public void assertAllowed(String identifier, String remoteAddress) {
        assertAllowed("login", identifier, remoteAddress);
    }

    @Transactional(readOnly = true)
    public void assertAllowed(String realm, String identifier, String remoteAddress) {
        if (usesRedis()) {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(redisKey(realm, identifier, remoteAddress));
            Instant lockedUntil = instantField(values, "lockedUntil");
            if (lockedUntil != null && lockedUntil.isAfter(now())) {
                throw tooManyRequests(realm);
            }
            return;
        }
        if (usesDatabase()) {
            SecurityRateLimitAttempt attempt = attemptRepository.findByAttemptKey(key(realm, identifier, remoteAddress)).orElse(null);
            if (attempt != null && attempt.getLockedUntil() != null && attempt.getLockedUntil().toInstant().isAfter(now())) {
                throw tooManyRequests(realm);
            }
            return;
        }
        AttemptState state = attempts.get(key(realm, identifier, remoteAddress));
        if (state != null && state.lockedUntil != null && state.lockedUntil.isAfter(now())) {
            throw tooManyRequests(realm);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String identifier, String remoteAddress) {
        recordFailure("login", identifier, remoteAddress, "auth.login_failed", "Invalid credentials");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String realm, String identifier, String remoteAddress, String eventType, String reason) {
        if (usesRedis()) {
            recordRedisFailure(realm, identifier, remoteAddress, eventType, reason);
            return;
        }
        if (usesDatabase()) {
            recordDatabaseFailure(realm, identifier, remoteAddress, eventType, reason);
            return;
        }
        attempts.compute(key(realm, identifier, remoteAddress), (ignored, existing) -> {
            Instant current = now();
            AttemptState state = existing == null || existing.firstFailureAt.plus(failureWindow).isBefore(current)
                    ? new AttemptState(current, 0, null)
                    : existing;
            int failures = state.failures + 1;
            Instant lockedUntil = failures >= maxFailures ? current.plus(lockoutDuration) : state.lockedUntil;
            return new AttemptState(state.firstFailureAt, failures, lockedUntil);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String identifier, String remoteAddress) {
        recordSuccess("login", identifier, remoteAddress);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String realm, String identifier, String remoteAddress) {
        if (usesRedis()) {
            redisTemplate.delete(redisKey(realm, identifier, remoteAddress));
            return;
        }
        if (usesDatabase()) {
            attemptRepository.deleteByAttemptKey(key(realm, identifier, remoteAddress));
            return;
        }
        attempts.remove(key(realm, identifier, remoteAddress));
    }

    private Instant now() {
        return clock.instant();
    }

    private void recordRedisFailure(String realm, String identifier, String remoteAddress, String eventType, String reason) {
        String attemptKey = redisKey(realm, identifier, remoteAddress);
        Map<Object, Object> values = redisTemplate.opsForHash().entries(attemptKey);
        Instant firstFailureAt = instantField(values, "firstFailureAt");
        boolean expiredWindow = firstFailureAt == null || firstFailureAt.plus(failureWindow).isBefore(now());
        int failures = expiredWindow ? 0 : intField(values, "failureCount");
        Instant lockedUntil = expiredWindow ? null : instantField(values, "lockedUntil");
        if (expiredWindow) {
            firstFailureAt = now();
            lockedUntil = null;
        }
        failures += 1;
        if (failures >= maxFailures) {
            lockedUntil = now().plus(lockoutDuration);
        }

        redisTemplate.opsForHash().put(attemptKey, "realm", normalized(realm));
        redisTemplate.opsForHash().put(attemptKey, "identifier", normalized(identifier));
        redisTemplate.opsForHash().put(attemptKey, "remoteAddress", truncate(trimToNull(remoteAddress), 120));
        redisTemplate.opsForHash().put(attemptKey, "firstFailureAt", firstFailureAt.toString());
        redisTemplate.opsForHash().put(attemptKey, "failureCount", Integer.toString(failures));
        if (lockedUntil == null) {
            redisTemplate.opsForHash().delete(attemptKey, "lockedUntil");
        } else {
            redisTemplate.opsForHash().put(attemptKey, "lockedUntil", lockedUntil.toString());
        }
        redisTemplate.expire(attemptKey, failureWindow.plus(lockoutDuration).plusMinutes(1));
        persistFailureEvent(realm, identifier, remoteAddress, eventType, reason);
    }

    private void recordDatabaseFailure(String realm, String identifier, String remoteAddress, String eventType, String reason) {
        String attemptKey = key(realm, identifier, remoteAddress);
        OffsetDateTime current = OffsetDateTime.ofInstant(now(), ZoneOffset.UTC);
        SecurityRateLimitAttempt attempt = attemptRepository.findByAttemptKey(attemptKey).orElseGet(() -> {
            SecurityRateLimitAttempt created = new SecurityRateLimitAttempt();
            created.setAttemptKey(attemptKey);
            created.setRealm(normalized(realm));
            created.setIdentifier(normalized(identifier));
            created.setRemoteAddress(truncate(trimToNull(remoteAddress), 120));
            created.setFirstFailureAt(current);
            created.setFailureCount(0);
            return created;
        });
        boolean expiredWindow = attempt.getFirstFailureAt() == null
                || attempt.getFirstFailureAt().plus(failureWindow).toInstant().isBefore(now());
        if (expiredWindow) {
            attempt.setFirstFailureAt(current);
            attempt.setFailureCount(0);
            attempt.setLockedUntil(null);
        }
        int failures = (attempt.getFailureCount() == null ? 0 : attempt.getFailureCount()) + 1;
        attempt.setFailureCount(failures);
        if (failures >= maxFailures) {
            attempt.setLockedUntil(current.plus(lockoutDuration));
        }
        attemptRepository.save(attempt);

        persistFailureEvent(realm, identifier, remoteAddress, eventType, reason);
    }

    private void persistFailureEvent(String realm, String identifier, String remoteAddress, String eventType, String reason) {
        if (failureEventRepository == null) {
            return;
        }
        SecurityAuthFailureEvent event = new SecurityAuthFailureEvent();
        event.setEventType(truncate(firstText(eventType, "auth.failure"), 120));
        event.setRealm(normalized(realm));
        event.setIdentifier(normalized(identifier));
        event.setRemoteAddress(truncate(trimToNull(remoteAddress), 120));
        event.setReason(truncate(firstText(reason, "Authentication failed"), 240));
        failureEventRepository.save(event);
    }

    private boolean usesRedis() {
        return "redis".equals(rateLimitStore);
    }

    private boolean usesDatabase() {
        return "database".equals(rateLimitStore) && attemptRepository != null && failureEventRepository != null;
    }

    private void validateStore() {
        if (!"memory".equals(rateLimitStore) && !"database".equals(rateLimitStore) && !"redis".equals(rateLimitStore)) {
            throw new IllegalStateException("Unsupported Trasck security rate-limit store: " + rateLimitStore);
        }
        if ("redis".equals(rateLimitStore) && redisTemplate == null) {
            throw new IllegalStateException("Redis rate-limit store requires a StringRedisTemplate bean");
        }
    }

    private String key(String realm, String identifier, String remoteAddress) {
        return normalized(realm)
                + "|"
                + normalized(identifier)
                + "|"
                + normalized(remoteAddress);
    }

    private String redisKey(String realm, String identifier, String remoteAddress) {
        return "trasck:security:rate-limit:" + sha256(key(realm, identifier, remoteAddress));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private String normalized(String value) {
        return (value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    private Instant instantField(Map<Object, Object> values, String fieldName) {
        Object value = values == null ? null : values.get(fieldName);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Instant.parse(value.toString());
    }

    private int intField(Map<Object, Object> values, String fieldName) {
        Object value = values == null ? null : values.get(fieldName);
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private ResponseStatusException tooManyRequests(String realm) {
        String target = "login".equals(normalized(realm)) ? "login" : "authentication";
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed " + target + " attempts; try again later");
    }

    private String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record AttemptState(Instant firstFailureAt, int failures, Instant lockedUntil) {
    }
}
