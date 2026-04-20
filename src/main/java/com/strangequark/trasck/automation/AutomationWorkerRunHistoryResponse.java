package com.strangequark.trasck.automation;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AutomationWorkerRunHistoryResponse(
        UUID id,
        UUID workspaceId,
        String workerType,
        String triggerType,
        String status,
        Boolean dryRun,
        Integer requestedLimit,
        Integer maxAttempts,
        Integer processedCount,
        Integer successCount,
        Integer failureCount,
        Integer deadLetterCount,
        String errorMessage,
        Object metadata,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    static AutomationWorkerRunHistoryResponse from(AutomationWorkerRun run) {
        return new AutomationWorkerRunHistoryResponse(
                run.getId(),
                run.getWorkspaceId(),
                run.getWorkerType(),
                run.getTriggerType(),
                run.getStatus(),
                run.getDryRun(),
                run.getRequestedLimit(),
                run.getMaxAttempts(),
                run.getProcessedCount(),
                run.getSuccessCount(),
                run.getFailureCount(),
                run.getDeadLetterCount(),
                run.getErrorMessage(),
                JsonValues.toJavaValue(run.getMetadata()),
                run.getStartedAt(),
                run.getFinishedAt()
        );
    }
}
