package com.strangequark.trasck.integration;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRecordRepository extends JpaRepository<ImportJobRecord, UUID> {
}
