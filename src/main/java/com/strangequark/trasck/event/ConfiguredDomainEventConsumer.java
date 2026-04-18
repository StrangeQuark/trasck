package com.strangequark.trasck.event;

public interface ConfiguredDomainEventConsumer {
    String consumerType();

    void handle(DomainEvent event, EventConsumerConfig config);
}
