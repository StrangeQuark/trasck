package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileProjectRepository extends JpaRepository<AgentProfileProject, AgentProfileProjectId> {
    boolean existsByIdAgentProfileId(UUID agentProfileId);

    boolean existsByIdAgentProfileIdAndIdProjectId(UUID agentProfileId, UUID projectId);

    List<AgentProfileProject> findByIdAgentProfileIdOrderByCreatedAtAsc(UUID agentProfileId);

    void deleteByIdAgentProfileId(UUID agentProfileId);
}
