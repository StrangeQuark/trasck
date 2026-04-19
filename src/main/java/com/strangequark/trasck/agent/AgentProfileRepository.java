package com.strangequark.trasck.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, UUID> {
    List<AgentProfile> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<AgentProfile> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
