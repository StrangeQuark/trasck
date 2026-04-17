package com.strangequark.trasck.activity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentStorageConfigRepository extends JpaRepository<AttachmentStorageConfig, UUID> {
}
