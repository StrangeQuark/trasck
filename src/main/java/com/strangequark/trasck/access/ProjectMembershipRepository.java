package com.strangequark.trasck.access;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    Optional<ProjectMembership> findByProjectIdAndUserIdAndStatusIgnoreCase(UUID projectId, UUID userId, String status);

    boolean existsByProjectIdAndUserIdAndStatusIgnoreCase(UUID projectId, UUID userId, String status);
}
