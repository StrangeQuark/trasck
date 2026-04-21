package com.strangequark.trasck.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<Label> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    Optional<Label> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
