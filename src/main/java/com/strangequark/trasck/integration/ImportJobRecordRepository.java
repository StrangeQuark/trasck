package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRecordRepository extends JpaRepository<ImportJobRecord, UUID> {
    List<ImportJobRecord> findByImportJobIdOrderBySourceTypeAscSourceIdAsc(UUID importJobId);

    Optional<ImportJobRecord> findByImportJobIdAndSourceTypeAndSourceId(UUID importJobId, String sourceType, String sourceId);
}
