package com.strangequark.trasck.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {
    boolean existsByDomainEventIdAndWorkspaceIdAndAction(UUID domainEventId, UUID workspaceId, String action);

    List<AuditLogEntry> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
}
