package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentDispatchAttemptRepository extends JpaRepository<AgentDispatchAttempt, UUID> {
    List<AgentDispatchAttempt> findByAgentTaskIdOrderByStartedAtAscIdAsc(UUID agentTaskId);
}
