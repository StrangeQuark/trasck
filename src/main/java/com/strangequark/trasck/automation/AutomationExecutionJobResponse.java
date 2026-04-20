package com.strangequark.trasck.automation;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AutomationExecutionJobResponse(
        UUID id,
        UUID ruleId,
        UUID workspaceId,
        String sourceEntityType,
        UUID sourceEntityId,
        String status,
        Object payload,
        Integer attempts,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        String lastError,
        OffsetDateTime createdAt,
        List<AutomationExecutionLogResponse> logs
) {
    static AutomationExecutionJobResponse from(AutomationExecutionJob job, List<AutomationExecutionLog> logs) {
        return new AutomationExecutionJobResponse(
                job.getId(),
                job.getRuleId(),
                job.getWorkspaceId(),
                job.getSourceEntityType(),
                job.getSourceEntityId(),
                job.getStatus(),
                JsonValues.toJavaValue(job.getPayload()),
                job.getAttempts(),
                job.getNextAttemptAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getFailedAt(),
                job.getLastError(),
                job.getCreatedAt(),
                logs.stream().map(AutomationExecutionLogResponse::from).toList()
        );
    }
}
