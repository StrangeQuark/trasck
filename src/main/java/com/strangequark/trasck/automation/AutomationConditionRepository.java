package com.strangequark.trasck.automation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationConditionRepository extends JpaRepository<AutomationCondition, UUID> {
}
