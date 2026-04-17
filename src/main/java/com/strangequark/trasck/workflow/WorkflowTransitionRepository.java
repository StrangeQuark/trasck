package com.strangequark.trasck.workflow;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {
    @Query("""
            select wt
            from WorkflowTransition wt
            where wt.workflowId = :workflowId
              and lower(wt.key) = lower(:key)
              and (wt.fromStatusId = :fromStatusId or wt.globalTransition = true)
            """)
    Optional<WorkflowTransition> findAllowedTransition(
            @Param("workflowId") UUID workflowId,
            @Param("key") String key,
            @Param("fromStatusId") UUID fromStatusId
    );
}
