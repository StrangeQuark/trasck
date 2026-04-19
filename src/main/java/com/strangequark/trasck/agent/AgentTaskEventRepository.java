package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskEventRepository extends JpaRepository<AgentTaskEvent, UUID> {
    List<AgentTaskEvent> findByAgentTaskIdOrderByCreatedAtAsc(UUID agentTaskId);
}
