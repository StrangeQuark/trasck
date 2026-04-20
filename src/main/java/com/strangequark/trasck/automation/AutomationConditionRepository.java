package com.strangequark.trasck.automation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationConditionRepository extends JpaRepository<AutomationCondition, UUID> {
    List<AutomationCondition> findByRuleIdOrderByPositionAsc(UUID ruleId);

    Optional<AutomationCondition> findByIdAndRuleId(UUID id, UUID ruleId);
}
