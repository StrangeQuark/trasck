package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    List<AutomationRule> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);
}
