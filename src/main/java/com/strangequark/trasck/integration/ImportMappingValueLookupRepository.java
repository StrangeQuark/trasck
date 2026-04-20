package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportMappingValueLookupRepository extends JpaRepository<ImportMappingValueLookup, UUID> {
    List<ImportMappingValueLookup> findByMappingTemplateIdOrderBySortOrderAscSourceFieldAsc(UUID mappingTemplateId);

    List<ImportMappingValueLookup> findByMappingTemplateIdAndEnabledTrueOrderBySortOrderAscSourceFieldAsc(UUID mappingTemplateId);

    Optional<ImportMappingValueLookup> findByIdAndMappingTemplateId(UUID id, UUID mappingTemplateId);
}
