package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationWorkerRunRepository extends JpaRepository<AutomationWorkerRun, UUID> {
    List<AutomationWorkerRun> findTop50ByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId);
}
