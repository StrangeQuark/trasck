package com.strangequark.trasck.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DomainEventService {

    private final DomainEventRepository domainEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DomainEventService(
            DomainEventRepository domainEventRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.domainEventRepository = domainEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public DomainEvent record(UUID workspaceId, String aggregateType, UUID aggregateId, String eventType, JsonNode payload) {
        DomainEvent event = new DomainEvent();
        event.setWorkspaceId(workspaceId);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setProcessingStatus("pending");
        event.setAttempts(0);
        DomainEvent saved = domainEventRepository.save(event);
        publishAfterCommit(saved);
        return saved;
    }

    private void publishAfterCommit(DomainEvent event) {
        DomainEventPublished published = new DomainEventPublished(
                event.getId(),
                event.getWorkspaceId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload()
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    applicationEventPublisher.publishEvent(published);
                }
            });
            return;
        }
        applicationEventPublisher.publishEvent(published);
    }
}
