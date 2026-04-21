package com.strangequark.trasck.access;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAdminRepository extends JpaRepository<SystemAdmin, UUID> {
    boolean existsByUserIdAndActiveTrue(UUID userId);

    Optional<SystemAdmin> findByUserId(UUID userId);

    long countByActiveTrue();
}
