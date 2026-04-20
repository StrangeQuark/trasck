package com.strangequark.trasck.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportJobRecordRepository extends JpaRepository<ImportJobRecord, UUID> {
    List<ImportJobRecord> findByImportJobIdOrderBySourceTypeAscSourceIdAsc(UUID importJobId);

    List<ImportJobRecord> findByImportJobIdAndStatusInOrderBySourceTypeAscSourceIdAsc(UUID importJobId, List<String> statuses);

    List<ImportJobRecord> findByImportJobIdAndConflictStatusOrderBySourceTypeAscSourceIdAsc(UUID importJobId, String conflictStatus);

    List<ImportJobRecord> findByIdInAndImportJobIdOrderBySourceTypeAscSourceIdAsc(List<UUID> ids, UUID importJobId);

    @Query("""
            select record
            from ImportJobRecord record
            where record.importJobId = :importJobId
              and (:status is null or record.status = :status)
              and (:conflictStatus is null or record.conflictStatus = :conflictStatus)
              and (:sourceType is null or lower(record.sourceType) = lower(:sourceType))
            order by record.sourceType asc, record.sourceId asc
            """)
    List<ImportJobRecord> findFiltered(
            @Param("importJobId") UUID importJobId,
            @Param("status") String status,
            @Param("conflictStatus") String conflictStatus,
            @Param("sourceType") String sourceType
    );

    long countByImportJobIdAndConflictStatus(UUID importJobId, String conflictStatus);

    Optional<ImportJobRecord> findByImportJobIdAndSourceTypeAndSourceId(UUID importJobId, String sourceType, String sourceId);
}
