package com.strangequark.trasck.event;

public record DomainEventReplayResult(
        int eventsMatched,
        int deliveriesReset
) {
}
