package com.strangequark.trasck.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    @Override
    @EntityGraph(attributePaths = {"workspace", "statuses", "transitions", "transitions.fromStatus", "transitions.toStatus"})
    Optional<Workflow> findById(UUID id);

    Optional<Workflow> findFirstByWorkspaceIdAndActiveTrueOrderByCreatedAtAsc(UUID workspaceId);
}
