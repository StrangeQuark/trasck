package com.strangequark.trasck.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityRateLimitAttemptRepository extends JpaRepository<SecurityRateLimitAttempt, UUID> {
    Optional<SecurityRateLimitAttempt> findByAttemptKey(String attemptKey);

    void deleteByAttemptKey(String attemptKey);
}
