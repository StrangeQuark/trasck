package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemLinkRepository extends JpaRepository<WorkItemLink, UUID> {
    List<WorkItemLink> findBySourceWorkItemIdOrTargetWorkItemIdOrderByCreatedAtAsc(UUID sourceWorkItemId, UUID targetWorkItemId);

    @Query("""
            select link from WorkItemLink link
            where link.id = :id
              and (link.sourceWorkItemId = :workItemId or link.targetWorkItemId = :workItemId)
            """)
    Optional<WorkItemLink> findByIdAndWorkItemId(@Param("id") UUID id, @Param("workItemId") UUID workItemId);

    boolean existsBySourceWorkItemIdAndTargetWorkItemIdAndLinkType(UUID sourceWorkItemId, UUID targetWorkItemId, String linkType);
}
