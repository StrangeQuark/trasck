package com.strangequark.trasck.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {
    boolean existsByDomainEventIdAndWorkspaceIdAndAction(UUID domainEventId, UUID workspaceId, String action);

    List<AuditLogEntry> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    long countByWorkspaceIdAndCreatedAtBefore(UUID workspaceId, OffsetDateTime cutoff);

    List<AuditLogEntry> findByWorkspaceIdAndCreatedAtBeforeOrderByCreatedAtAsc(UUID workspaceId, OffsetDateTime cutoff, Pageable pageable);

    @Modifying
    @Query("""
            delete from AuditLogEntry entry
            where entry.workspaceId = :workspaceId
              and entry.createdAt < :cutoff
            """)
    int deleteRetainedEntries(@Param("workspaceId") UUID workspaceId, @Param("cutoff") OffsetDateTime cutoff);
}
