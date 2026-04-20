package com.strangequark.trasck.automation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AutomationWorkerHealthResponse(
        UUID workspaceId,
        String workerType,
        UUID lastRunId,
        String lastStatus,
        OffsetDateTime lastStartedAt,
        OffsetDateTime lastFinishedAt,
        Integer consecutiveFailures,
        String lastError,
        OffsetDateTime updatedAt
) {
    static AutomationWorkerHealthResponse from(AutomationWorkerHealth health) {
        return new AutomationWorkerHealthResponse(
                health.getWorkspaceId(),
                health.getWorkerType(),
                health.getLastRunId(),
                health.getLastStatus(),
                health.getLastStartedAt(),
                health.getLastFinishedAt(),
                health.getConsecutiveFailures(),
                health.getLastError(),
                health.getUpdatedAt()
        );
    }
}
