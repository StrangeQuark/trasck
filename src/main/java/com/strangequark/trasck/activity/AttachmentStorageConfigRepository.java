package com.strangequark.trasck.activity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentStorageConfigRepository extends JpaRepository<AttachmentStorageConfig, UUID> {
    Optional<AttachmentStorageConfig> findFirstByWorkspaceIdAndActiveTrueAndDefaultConfigTrue(UUID workspaceId);

    Optional<AttachmentStorageConfig> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<AttachmentStorageConfig> findByIdAndWorkspaceIdAndActiveTrue(UUID id, UUID workspaceId);
}
