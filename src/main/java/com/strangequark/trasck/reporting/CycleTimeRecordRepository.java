package com.strangequark.trasck.reporting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CycleTimeRecordRepository extends JpaRepository<CycleTimeRecord, UUID> {
}
