package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolutionRepository extends JpaRepository<Resolution, UUID> {
    List<Resolution> findByWorkspaceIdOrderBySortOrderAscNameAsc(UUID workspaceId);
}
