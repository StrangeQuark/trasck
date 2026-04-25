package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectWorkItemTypeRepository extends JpaRepository<ProjectWorkItemType, UUID> {
    Optional<ProjectWorkItemType> findByProjectIdAndWorkItemTypeId(UUID projectId, UUID workItemTypeId);

    List<ProjectWorkItemType> findByProjectId(UUID projectId);
}
