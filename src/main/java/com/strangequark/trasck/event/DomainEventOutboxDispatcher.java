package com.strangequark.trasck.event;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DomainEventOutboxDispatcher {

    private final DomainEventRepository domainEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final int batchSize;

    public DomainEventOutboxDispatcher(
            DomainEventRepository domainEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            @Value("${trasck.events.outbox.batch-size:50}") int batchSize
    ) {
        this.domainEventRepository = domainEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.batchSize = batchSize;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(DomainEventPublished published) {
        domainEventRepository.findById(published.eventId()).ifPresent(this::markPublished);
    }

    @Scheduled(fixedDelayString = "${trasck.events.outbox.fixed-delay-ms:5000}")
    @Transactional
    public void dispatchPending() {
        for (DomainEvent event : domainEventRepository.findByProcessingStatusOrderByOccurredAtAsc("pending", PageRequest.of(0, batchSize))) {
            try {
                applicationEventPublisher.publishEvent(toPublished(event));
                markPublished(event);
            } catch (RuntimeException ex) {
                event.setProcessingStatus("failed");
                event.setAttempts(attempts(event) + 1);
                event.setLastError(ex.getMessage());
            }
        }
    }

    private DomainEventPublished toPublished(DomainEvent event) {
        return new DomainEventPublished(
                event.getId(),
                event.getWorkspaceId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload()
        );
    }

    private void markPublished(DomainEvent event) {
        if ("published".equals(event.getProcessingStatus())) {
            return;
        }
        event.setProcessingStatus("published");
        event.setAttempts(attempts(event) + 1);
        event.setLastError(null);
        event.setPublishedAt(OffsetDateTime.now());
    }

    private int attempts(DomainEvent event) {
        return event.getAttempts() == null ? 0 : event.getAttempts();
    }
}
