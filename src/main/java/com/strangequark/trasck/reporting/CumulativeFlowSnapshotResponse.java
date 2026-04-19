package com.strangequark.trasck.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CumulativeFlowSnapshotResponse(
        UUID id,
        UUID boardId,
        LocalDate snapshotDate,
        UUID statusId,
        Integer workItemCount,
        BigDecimal totalPoints
) {
    static CumulativeFlowSnapshotResponse from(CumulativeFlowSnapshot snapshot) {
        return new CumulativeFlowSnapshotResponse(
                snapshot.getId(),
                snapshot.getBoardId(),
                snapshot.getSnapshotDate(),
                snapshot.getStatusId(),
                snapshot.getWorkItemCount(),
                snapshot.getTotalPoints()
        );
    }
}
