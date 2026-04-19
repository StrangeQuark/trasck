package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentArtifactRepository extends JpaRepository<AgentArtifact, UUID> {
    List<AgentArtifact> findByAgentTaskIdOrderByCreatedAtAsc(UUID agentTaskId);

    boolean existsByAgentTaskIdAndArtifactTypeAndName(UUID agentTaskId, String artifactType, String name);
}
