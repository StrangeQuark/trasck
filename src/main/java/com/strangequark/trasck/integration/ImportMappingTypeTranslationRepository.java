package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportMappingTypeTranslationRepository extends JpaRepository<ImportMappingTypeTranslation, UUID> {
    List<ImportMappingTypeTranslation> findByMappingTemplateIdOrderBySourceTypeKeyAsc(UUID mappingTemplateId);

    List<ImportMappingTypeTranslation> findByMappingTemplateIdAndEnabledTrueOrderBySourceTypeKeyAsc(UUID mappingTemplateId);

    Optional<ImportMappingTypeTranslation> findByIdAndMappingTemplateId(UUID id, UUID mappingTemplateId);
}
