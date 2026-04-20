package com.strangequark.trasck.integration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailProviderSettingsRepository extends JpaRepository<EmailProviderSettings, UUID> {
    Optional<EmailProviderSettings> findByWorkspaceId(UUID workspaceId);
}
