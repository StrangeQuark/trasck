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
