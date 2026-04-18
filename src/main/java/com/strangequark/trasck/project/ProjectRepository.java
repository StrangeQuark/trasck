package com.strangequark.trasck.project;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByWorkspaceIdAndKeyIgnoreCase(UUID workspaceId, String key);

    Optional<Project> findByWorkspaceIdAndKeyIgnoreCase(UUID workspaceId, String key);

    @EntityGraph(attributePaths = {"workspace", "parentProject"})
    Optional<Project> findByIdAndDeletedAtIsNull(UUID id);
}
