package com.strangequark.trasck.event;

public interface DomainEventConsumer {
    String consumerKey();

    void handle(DomainEvent event);
}
