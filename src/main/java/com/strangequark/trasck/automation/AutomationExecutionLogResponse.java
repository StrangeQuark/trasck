package com.strangequark.trasck.automation;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AutomationExecutionLogResponse(
        UUID id,
        UUID jobId,
        UUID actionId,
        String status,
        String message,
        Object metadata,
        OffsetDateTime createdAt
) {
    static AutomationExecutionLogResponse from(AutomationExecutionLog log) {
        return new AutomationExecutionLogResponse(
                log.getId(),
                log.getJobId(),
                log.getActionId(),
                log.getStatus(),
                log.getMessage(),
                JsonValues.toJavaValue(log.getMetadata()),
                log.getCreatedAt()
        );
    }
}
