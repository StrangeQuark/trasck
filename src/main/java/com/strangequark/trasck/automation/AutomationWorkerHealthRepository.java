package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationWorkerHealthRepository extends JpaRepository<AutomationWorkerHealth, AutomationWorkerHealthId> {
    List<AutomationWorkerHealth> findByWorkspaceIdOrderByWorkerTypeAsc(UUID workspaceId);

    List<AutomationWorkerHealth> findByWorkspaceIdAndWorkerTypeOrderByWorkerTypeAsc(UUID workspaceId, String workerType);
}
