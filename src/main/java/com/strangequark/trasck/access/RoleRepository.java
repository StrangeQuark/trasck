package com.strangequark.trasck.access;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByWorkspaceIdAndKeyIgnoreCaseAndProjectIdIsNull(UUID workspaceId, String key);

    Optional<Role> findByIdAndWorkspaceIdAndProjectIdIsNull(UUID id, UUID workspaceId);

    Optional<Role> findByIdAndProjectId(UUID id, UUID projectId);
}
