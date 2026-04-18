package com.strangequark.trasck.activity;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityEventResponse(
        UUID id,
        UUID domainEventId,
        UUID workspaceId,
        UUID actorId,
        String entityType,
        UUID entityId,
        String eventType,
        Object metadata,
        OffsetDateTime createdAt
) {
    static ActivityEventResponse from(ActivityEvent event) {
        return new ActivityEventResponse(
                event.getId(),
                event.getDomainEventId(),
                event.getWorkspaceId(),
                event.getActorId(),
                event.getEntityType(),
                event.getEntityId(),
                event.getEventType(),
                JsonValues.toJavaValue(event.getMetadata()),
                event.getCreatedAt()
        );
    }
}
