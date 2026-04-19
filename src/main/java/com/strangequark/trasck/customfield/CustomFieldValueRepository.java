package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, UUID> {
    List<CustomFieldValue> findByWorkItemIdOrderByCustomFieldIdAsc(UUID workItemId);

    Optional<CustomFieldValue> findByWorkItemIdAndCustomFieldId(UUID workItemId, UUID customFieldId);

    long countByCustomFieldId(UUID customFieldId);
}
