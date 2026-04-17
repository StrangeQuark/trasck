package com.strangequark.trasck.workspace;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    boolean existsByOrganizationIdAndKeyIgnoreCase(UUID organizationId, String key);

    Optional<Workspace> findByOrganizationIdAndKeyIgnoreCase(UUID organizationId, String key);
}
