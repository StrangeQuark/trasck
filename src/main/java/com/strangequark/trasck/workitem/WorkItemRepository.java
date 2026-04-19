package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {
    @EntityGraph(attributePaths = {"workspace", "project", "project.workspace", "type", "status", "priority", "parent"})
    Optional<WorkItem> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = {"project", "type", "status", "priority"})
    List<WorkItem> findByProjectIdAndDeletedAtIsNullOrderByRankAsc(UUID projectId);

    @Query(value = """
            select *
            from work_items
            where project_id = :projectId
              and deleted_at is null
            order by rank asc, id asc
            limit :limit
            """, nativeQuery = true)
    List<WorkItem> findProjectFirstCursorPage(
            @Param("projectId") UUID projectId,
            @Param("limit") int limit
    );

    @Query(value = """
            select wi.*
            from work_items wi
            join custom_field_values cfv on cfv.work_item_id = wi.id
            where wi.project_id = :projectId
              and wi.deleted_at is null
              and cfv.custom_field_id = :customFieldId
              and coalesce(cfv.value #>> '{}', '') = :customFieldValue
            order by wi.rank asc, wi.id asc
            limit :limit
            """, nativeQuery = true)
    List<WorkItem> findProjectFirstCursorPageByCustomField(
            @Param("projectId") UUID projectId,
            @Param("customFieldId") UUID customFieldId,
            @Param("customFieldValue") String customFieldValue,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from work_items
            where project_id = :projectId
              and deleted_at is null
              and (
                  rank > :cursorRank
                  or (rank = :cursorRank and id::text > :cursorId)
              )
            order by rank asc, id asc
            limit :limit
            """, nativeQuery = true)
    List<WorkItem> findProjectCursorPageAfter(
            @Param("projectId") UUID projectId,
            @Param("cursorRank") String cursorRank,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    @Query(value = """
            select wi.*
            from work_items wi
            join custom_field_values cfv on cfv.work_item_id = wi.id
            where wi.project_id = :projectId
              and wi.deleted_at is null
              and cfv.custom_field_id = :customFieldId
              and coalesce(cfv.value #>> '{}', '') = :customFieldValue
              and (
                  wi.rank > :cursorRank
                  or (wi.rank = :cursorRank and wi.id::text > :cursorId)
              )
            order by wi.rank asc, wi.id asc
            limit :limit
            """, nativeQuery = true)
    List<WorkItem> findProjectCursorPageAfterByCustomField(
            @Param("projectId") UUID projectId,
            @Param("customFieldId") UUID customFieldId,
            @Param("customFieldValue") String customFieldValue,
            @Param("cursorRank") String cursorRank,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    List<WorkItem> findByParentIdAndDeletedAtIsNull(UUID parentId);

    Optional<WorkItem> findTopByProjectIdAndDeletedAtIsNullOrderByRankDesc(UUID projectId);

    @EntityGraph(attributePaths = {"project", "type", "status", "priority"})
    @Query("""
            select wi
            from WorkItem wi
            where wi.id = :id
              and wi.projectId = :projectId
              and wi.deletedAt is null
            """)
    Optional<WorkItem> findActiveInProject(@Param("id") UUID id, @Param("projectId") UUID projectId);
}
