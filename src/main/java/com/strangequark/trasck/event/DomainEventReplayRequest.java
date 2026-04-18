package com.strangequark.trasck.event;

import java.util.List;
import java.util.UUID;

public record DomainEventReplayRequest(
        List<UUID> eventIds,
        List<String> consumerKeys,
        Boolean includePublished
) {
}
