package com.strangequark.trasck.access;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    boolean existsByWorkspaceIdAndUserIdAndStatusIgnoreCase(UUID workspaceId, UUID userId, String status);

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserIdAndStatusIgnoreCase(UUID workspaceId, UUID userId, String status);
}
