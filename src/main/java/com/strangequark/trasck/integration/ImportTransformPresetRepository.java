package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportTransformPresetRepository extends JpaRepository<ImportTransformPreset, UUID> {
    List<ImportTransformPreset> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<ImportTransformPreset> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
