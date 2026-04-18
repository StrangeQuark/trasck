package com.strangequark.trasck.event;

import java.util.List;
import java.util.UUID;

public record DomainEventReplayResponse(
        UUID workspaceId,
        List<String> consumerKeys,
        boolean includePublished,
        int eventsMatched,
        int deliveriesReset
) {
}
