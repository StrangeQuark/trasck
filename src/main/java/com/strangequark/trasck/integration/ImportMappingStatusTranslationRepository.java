package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportMappingStatusTranslationRepository extends JpaRepository<ImportMappingStatusTranslation, UUID> {
    List<ImportMappingStatusTranslation> findByMappingTemplateIdOrderBySourceStatusKeyAsc(UUID mappingTemplateId);

    List<ImportMappingStatusTranslation> findByMappingTemplateIdAndEnabledTrueOrderBySourceStatusKeyAsc(UUID mappingTemplateId);

    Optional<ImportMappingStatusTranslation> findByIdAndMappingTemplateId(UUID id, UUID mappingTemplateId);
}
