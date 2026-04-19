package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IterationSnapshotResponse(
        UUID id,
        UUID iterationId,
        LocalDate snapshotDate,
        BigDecimal committedPoints,
        BigDecimal completedPoints,
        BigDecimal remainingPoints,
        BigDecimal scopeAddedPoints,
        BigDecimal scopeRemovedPoints
) {
    static IterationSnapshotResponse from(IterationSnapshot snapshot) {
        return new IterationSnapshotResponse(
                snapshot.getId(),
                snapshot.getIterationId(),
                snapshot.getSnapshotDate(),
                snapshot.getCommittedPoints(),
                snapshot.getCompletedPoints(),
                snapshot.getRemainingPoints(),
                snapshot.getScopeAddedPoints(),
                snapshot.getScopeRemovedPoints()
        );
    }
}
