package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldConfigurationRepository extends JpaRepository<FieldConfiguration, UUID> {
    List<FieldConfiguration> findByWorkspaceIdOrderByCustomFieldIdAscProjectIdAscWorkItemTypeIdAsc(UUID workspaceId);

    List<FieldConfiguration> findByCustomFieldIdOrderByProjectIdAscWorkItemTypeIdAsc(UUID customFieldId);

    Optional<FieldConfiguration> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
