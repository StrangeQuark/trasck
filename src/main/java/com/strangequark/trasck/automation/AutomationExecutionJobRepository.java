package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionJobRepository extends JpaRepository<AutomationExecutionJob, UUID> {
    List<AutomationExecutionJob> findByRuleIdOrderByCreatedAtDesc(UUID ruleId);
}
