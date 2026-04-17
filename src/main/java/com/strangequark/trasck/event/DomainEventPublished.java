package com.strangequark.trasck.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record DomainEventPublished(
        UUID eventId,
        UUID workspaceId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        JsonNode payload
) {
}
