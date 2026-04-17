package com.strangequark.trasck.automation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionJobRepository extends JpaRepository<AutomationExecutionJob, UUID> {
}
