package com.strangequark.trasck.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IterationResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID teamId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        BigDecimal committedPoints,
        BigDecimal completedPoints
) {
    static IterationResponse from(Iteration iteration) {
        return new IterationResponse(
                iteration.getId(),
                iteration.getWorkspaceId(),
                iteration.getProjectId(),
                iteration.getTeamId(),
                iteration.getName(),
                iteration.getStartDate(),
                iteration.getEndDate(),
                iteration.getStatus(),
                iteration.getCommittedPoints(),
                iteration.getCompletedPoints()
        );
    }
}
