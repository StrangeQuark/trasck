package com.strangequark.trasck.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    boolean existsByWorkspaceIdAndUserIdAndStatusIgnoreCase(UUID workspaceId, UUID userId, String status);

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserIdAndStatusIgnoreCase(UUID workspaceId, UUID userId, String status);

    List<WorkspaceMembership> findByUserIdAndStatusIgnoreCase(UUID userId, String status);
}
