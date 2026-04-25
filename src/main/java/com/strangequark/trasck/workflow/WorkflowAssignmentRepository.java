package com.strangequark.trasck.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowAssignmentRepository extends JpaRepository<WorkflowAssignment, UUID> {
    Optional<WorkflowAssignment> findByProjectIdAndWorkItemTypeId(UUID projectId, UUID workItemTypeId);

    List<WorkflowAssignment> findByProjectIdOrderByWorkflowIdAscWorkItemTypeIdAsc(UUID projectId);
}
