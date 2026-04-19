package com.strangequark.trasck.project;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
    Optional<Program> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
