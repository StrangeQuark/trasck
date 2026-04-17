package com.strangequark.trasck.workflow;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionActionRepository extends JpaRepository<WorkflowTransitionAction, UUID> {
}
