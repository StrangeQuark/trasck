package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportMaterializationRunRepository extends JpaRepository<ImportMaterializationRun, UUID> {
    List<ImportMaterializationRun> findByImportJobIdOrderByCreatedAtDesc(UUID importJobId);
}
