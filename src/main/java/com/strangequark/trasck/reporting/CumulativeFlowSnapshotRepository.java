package com.strangequark.trasck.reporting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CumulativeFlowSnapshotRepository extends JpaRepository<CumulativeFlowSnapshot, UUID> {
}
