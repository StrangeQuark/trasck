package com.strangequark.trasck.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CumulativeFlowSnapshotRepository extends JpaRepository<CumulativeFlowSnapshot, UUID> {
    List<CumulativeFlowSnapshot> findByBoardIdInAndSnapshotDateBetweenOrderBySnapshotDateAscStatusIdAsc(
            List<UUID> boardIds,
            LocalDate fromDate,
            LocalDate toDate
    );
}
