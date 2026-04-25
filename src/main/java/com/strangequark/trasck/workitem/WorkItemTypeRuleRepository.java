package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemTypeRuleRepository extends JpaRepository<WorkItemTypeRule, UUID> {
    boolean existsByWorkspaceIdAndParentTypeIdAndChildTypeIdAndEnabledTrue(UUID workspaceId, UUID parentTypeId, UUID childTypeId);

    List<WorkItemTypeRule> findByWorkspaceId(UUID workspaceId);
}
