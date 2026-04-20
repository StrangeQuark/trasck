package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportTransformPresetVersionRepository extends JpaRepository<ImportTransformPresetVersion, UUID> {
    List<ImportTransformPresetVersion> findByPresetIdOrderByVersionDesc(UUID presetId);

    Optional<ImportTransformPresetVersion> findByIdAndPresetId(UUID id, UUID presetId);
}
