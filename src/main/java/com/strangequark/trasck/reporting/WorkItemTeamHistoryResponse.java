package com.strangequark.trasck.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkItemTeamHistoryResponse(
        UUID id,
        UUID workItemId,
        UUID fromTeamId,
        UUID toTeamId,
        UUID changedById,
        OffsetDateTime changedAt
) {
    static WorkItemTeamHistoryResponse from(WorkItemTeamHistory history) {
        return new WorkItemTeamHistoryResponse(
                history.getId(),
                history.getWorkItemId(),
                history.getFromTeamId(),
                history.getToTeamId(),
                history.getChangedById(),
                history.getChangedAt()
        );
    }
}
