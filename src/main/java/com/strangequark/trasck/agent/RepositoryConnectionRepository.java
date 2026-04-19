package com.strangequark.trasck.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryConnectionRepository extends JpaRepository<RepositoryConnection, UUID> {
    List<RepositoryConnection> findByWorkspaceIdAndActiveTrueOrderByCreatedAtAsc(UUID workspaceId);

    Optional<RepositoryConnection> findByIdAndWorkspaceIdAndActiveTrue(UUID id, UUID workspaceId);
}
