package com.strangequark.trasck.reporting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportingIterationRollupRepository extends JpaRepository<ReportingIterationRollup, UUID> {
}
