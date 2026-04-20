package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionLogRepository extends JpaRepository<AutomationExecutionLog, UUID> {
    List<AutomationExecutionLog> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
