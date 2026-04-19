package com.strangequark.trasck.activity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    boolean existsByDomainEventIdAndEntityTypeAndEntityId(UUID domainEventId, String entityType, UUID entityId);

    List<ActivityEvent> findByWorkspaceIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID workspaceId,
            String entityType,
            UUID entityId,
            Pageable pageable
    );

    @Query(value = """
            select *
            from activity_events
            where workspace_id = :workspaceId
              and entity_type = :entityType
              and entity_id = :entityId
            order by created_at desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<ActivityEvent> findFirstCursorPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from activity_events
            where workspace_id = :workspaceId
              and entity_type = :entityType
              and entity_id = :entityId
              and (
                  created_at < :cursorCreatedAt
                  or (created_at = :cursorCreatedAt and id::text < :cursorId)
              )
            order by created_at desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<ActivityEvent> findCursorPageAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );
}
