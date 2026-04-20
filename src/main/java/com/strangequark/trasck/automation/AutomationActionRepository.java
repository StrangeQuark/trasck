package com.strangequark.trasck.automation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationActionRepository extends JpaRepository<AutomationAction, UUID> {
    List<AutomationAction> findByRuleIdOrderByPositionAsc(UUID ruleId);

    Optional<AutomationAction> findByIdAndRuleId(UUID id, UUID ruleId);
}
