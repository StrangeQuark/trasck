package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    List<Webhook> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<Webhook> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
