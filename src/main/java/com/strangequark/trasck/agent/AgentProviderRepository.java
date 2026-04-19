package com.strangequark.trasck.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProviderRepository extends JpaRepository<AgentProvider, UUID> {
    List<AgentProvider> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<AgentProvider> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<AgentProvider> findByWorkspaceIdAndProviderKey(UUID workspaceId, String providerKey);
}
