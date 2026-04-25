package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemTypeRepository extends JpaRepository<WorkItemType, UUID> {
    Optional<WorkItemType> findByWorkspaceIdAndKeyIgnoreCase(UUID workspaceId, String key);

    List<WorkItemType> findByWorkspaceIdOrderByHierarchyLevelDescNameAsc(UUID workspaceId);
}
