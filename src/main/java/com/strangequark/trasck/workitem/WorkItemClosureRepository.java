package com.strangequark.trasck.workitem;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemClosureRepository extends JpaRepository<WorkItemClosure, WorkItemClosureId> {
    boolean existsByIdAncestorWorkItemIdAndIdDescendantWorkItemId(java.util.UUID ancestorWorkItemId, java.util.UUID descendantWorkItemId);

    List<WorkItemClosure> findByIdDescendantWorkItemIdOrderByDepthAsc(java.util.UUID descendantWorkItemId);
}
