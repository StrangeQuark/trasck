package com.strangequark.trasck.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    boolean existsByDomainEventIdAndEntityTypeAndEntityId(UUID domainEventId, String entityType, UUID entityId);

    List<ActivityEvent> findByWorkspaceIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID workspaceId,
            String entityType,
            UUID entityId,
            Pageable pageable
    );
}
