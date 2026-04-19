package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldContextRepository extends JpaRepository<CustomFieldContext, UUID> {
    List<CustomFieldContext> findByCustomFieldIdOrderByProjectIdAscWorkItemTypeIdAsc(UUID customFieldId);

    Optional<CustomFieldContext> findByIdAndCustomFieldId(UUID id, UUID customFieldId);
}
