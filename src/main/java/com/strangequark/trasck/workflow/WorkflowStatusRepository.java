package com.strangequark.trasck.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {
    Optional<WorkflowStatus> findByWorkflowIdAndKeyIgnoreCase(UUID workflowId, String key);

    List<WorkflowStatus> findByWorkflowIdOrderBySortOrderAsc(UUID workflowId);
}
