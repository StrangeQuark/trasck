package com.strangequark.trasck.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {
    List<AgentTask> findByWorkItemIdOrderByQueuedAtDesc(UUID workItemId);

    Optional<AgentTask> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByAgentProfileIdAndStatusIn(UUID agentProfileId, List<String> statuses);
}
