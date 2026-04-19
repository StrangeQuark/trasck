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

    @Query(value = """
            select *
            from audit_log_entries
            where workspace_id = :workspaceId
            order by created_at desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<AuditLogEntry> findFirstCursorPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from audit_log_entries
            where workspace_id = :workspaceId
              and (
                  created_at < :cursorCreatedAt
                  or (created_at = :cursorCreatedAt and id::text < :cursorId)
              )
            order by created_at desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<AuditLogEntry> findCursorPageAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

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
