package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreenAssignmentRepository extends JpaRepository<ScreenAssignment, UUID> {
    List<ScreenAssignment> findByScreenIdOrderByPriorityAsc(UUID screenId);

    Optional<ScreenAssignment> findByIdAndScreenId(UUID id, UUID screenId);

    @Query("""
            select assignment
            from ScreenAssignment assignment
            join Screen screen on screen.id = assignment.screenId
            where screen.workspaceId = :workspaceId
              and assignment.operation = :operation
              and (assignment.projectId is null or assignment.projectId = :projectId)
              and (assignment.workItemTypeId is null or assignment.workItemTypeId = :workItemTypeId)
            order by
              case when assignment.projectId is null then 1 else 0 end,
              case when assignment.workItemTypeId is null then 1 else 0 end,
              assignment.priority asc
            """)
    List<ScreenAssignment> findApplicable(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectId") UUID projectId,
            @Param("workItemTypeId") UUID workItemTypeId,
            @Param("operation") String operation
    );
}
