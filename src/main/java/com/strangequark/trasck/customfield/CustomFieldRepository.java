package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {
    List<CustomField> findByWorkspaceIdOrderByKeyAsc(UUID workspaceId);

    Optional<CustomField> findByWorkspaceIdAndKeyIgnoreCase(UUID workspaceId, String key);

    boolean existsByWorkspaceIdAndKeyIgnoreCase(UUID workspaceId, String key);
}
