package com.strangequark.trasck.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IterationRepository extends JpaRepository<Iteration, UUID> {
    Optional<Iteration> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Iteration> findByIdAndProjectId(UUID id, UUID projectId);

    List<Iteration> findByProjectIdOrderByStartDateAscNameAsc(UUID projectId);
}
