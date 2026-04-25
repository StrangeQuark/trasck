package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityRepository extends JpaRepository<Priority, UUID> {
    Optional<Priority> findByWorkspaceIdAndKeyIgnoreCase(UUID workspaceId, String key);

    Optional<Priority> findByWorkspaceIdAndIsDefaultTrue(UUID workspaceId);

    List<Priority> findByWorkspaceIdOrderBySortOrderAscNameAsc(UUID workspaceId);
}
