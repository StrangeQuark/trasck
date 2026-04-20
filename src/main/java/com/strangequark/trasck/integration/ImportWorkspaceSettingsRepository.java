package com.strangequark.trasck.integration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportWorkspaceSettingsRepository extends JpaRepository<ImportWorkspaceSettings, UUID> {
    Optional<ImportWorkspaceSettings> findByWorkspaceId(UUID workspaceId);
}
