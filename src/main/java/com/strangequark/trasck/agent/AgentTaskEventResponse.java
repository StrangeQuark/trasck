package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentTaskEventResponse(
        UUID id,
        UUID agentTaskId,
        String eventType,
        String severity,
        String message,
        Object metadata,
        OffsetDateTime createdAt
) {
    static AgentTaskEventResponse from(AgentTaskEvent event) {
        return new AgentTaskEventResponse(
                event.getId(),
                event.getAgentTaskId(),
                event.getEventType(),
                event.getSeverity(),
                event.getMessage(),
                JsonValues.toJavaValue(event.getMetadata()),
                event.getCreatedAt()
        );
    }
}
