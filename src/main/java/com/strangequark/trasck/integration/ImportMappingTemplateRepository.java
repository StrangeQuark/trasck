package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportMappingTemplateRepository extends JpaRepository<ImportMappingTemplate, UUID> {
    List<ImportMappingTemplate> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<ImportMappingTemplate> findByIdInAndWorkspaceIdOrderByNameAsc(List<UUID> ids, UUID workspaceId);

    Optional<ImportMappingTemplate> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
