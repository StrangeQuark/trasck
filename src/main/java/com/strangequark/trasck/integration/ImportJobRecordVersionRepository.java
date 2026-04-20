package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRecordVersionRepository extends JpaRepository<ImportJobRecordVersion, UUID> {
    List<ImportJobRecordVersion> findByImportJobRecordIdOrderByVersionDesc(UUID importJobRecordId);

    Optional<ImportJobRecordVersion> findFirstByImportJobRecordIdOrderByVersionDesc(UUID importJobRecordId);
}
