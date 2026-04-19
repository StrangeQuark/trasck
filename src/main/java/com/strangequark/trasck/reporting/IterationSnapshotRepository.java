package com.strangequark.trasck.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IterationSnapshotRepository extends JpaRepository<IterationSnapshot, UUID> {
    List<IterationSnapshot> findByIterationIdOrderBySnapshotDateAsc(UUID iterationId);

    List<IterationSnapshot> findByIterationIdInAndSnapshotDateBetweenOrderBySnapshotDateAsc(List<UUID> iterationIds, LocalDate fromDate, LocalDate toDate);
}
