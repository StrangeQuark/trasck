package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VelocitySnapshotResponse(
        UUID id,
        UUID teamId,
        UUID iterationId,
        LocalDate snapshotDate,
        BigDecimal committedPoints,
        BigDecimal completedPoints,
        BigDecimal carriedOverPoints
) {
    static VelocitySnapshotResponse from(VelocitySnapshot snapshot) {
        return new VelocitySnapshotResponse(
                snapshot.getId(),
                snapshot.getTeamId(),
                snapshot.getIterationId(),
                null,
                snapshot.getCommittedPoints(),
                snapshot.getCompletedPoints(),
                snapshot.getCarriedOverPoints()
        );
    }
}
