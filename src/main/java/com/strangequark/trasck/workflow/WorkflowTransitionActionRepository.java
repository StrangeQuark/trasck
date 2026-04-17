package com.strangequark.trasck.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionActionRepository extends JpaRepository<WorkflowTransitionAction, UUID> {
    List<WorkflowTransitionAction> findByTransitionIdAndEnabledTrueOrderByPositionAsc(UUID transitionId);
}
