package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportConflictResolutionJobRepository extends JpaRepository<ImportConflictResolutionJob, UUID> {
    List<ImportConflictResolutionJob> findByImportJobIdOrderByRequestedAtDesc(UUID importJobId);
}
