package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {
    List<AgentMessage> findByAgentTaskIdOrderByCreatedAtAsc(UUID agentTaskId);
}
