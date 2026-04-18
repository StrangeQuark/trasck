package com.strangequark.trasck.event;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DomainEventOutboxDispatcher {

    private final DomainEventRepository domainEventRepository;
    private final DomainEventDeliveryRepository domainEventDeliveryRepository;
    private final List<DomainEventConsumer> consumers;
    private final int batchSize;

    public DomainEventOutboxDispatcher(
            DomainEventRepository domainEventRepository,
            DomainEventDeliveryRepository domainEventDeliveryRepository,
            List<DomainEventConsumer> consumers,
            @Value("${trasck.events.outbox.batch-size:50}") int batchSize
    ) {
        this.domainEventRepository = domainEventRepository;
        this.domainEventDeliveryRepository = domainEventDeliveryRepository;
        this.consumers = consumers;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${trasck.events.outbox.fixed-delay-ms:5000}")
    @Transactional
    public void dispatchPending() {
        if (consumers.isEmpty()) {
            return;
        }
        for (DomainEvent event : domainEventRepository.findByProcessingStatusInOrderByOccurredAtAsc(
                List.of("pending", "failed"),
                PageRequest.of(0, batchSize)
        )) {
            dispatch(event);
        }
    }

    private void dispatch(DomainEvent event) {
        ensureDeliveries(event);
        List<DomainEventDelivery> deliveries = domainEventDeliveryRepository.findByDomainEventIdAndDeliveryStatusIn(
                event.getId(),
                List.of("pending", "failed")
        );
        for (DomainEventDelivery delivery : deliveries) {
            deliver(event, delivery);
        }
        refreshEventStatus(event);
    }

    private void ensureDeliveries(DomainEvent event) {
        for (DomainEventConsumer consumer : consumers) {
            domainEventDeliveryRepository.findByDomainEventIdAndConsumerKey(event.getId(), consumer.consumerKey())
                    .orElseGet(() -> {
                        DomainEventDelivery delivery = new DomainEventDelivery();
                        delivery.setDomainEventId(event.getId());
                        delivery.setConsumerKey(consumer.consumerKey());
                        delivery.setDeliveryStatus("pending");
                        delivery.setAttempts(0);
                        return domainEventDeliveryRepository.save(delivery);
                    });
        }
    }

    private void deliver(DomainEvent event, DomainEventDelivery delivery) {
        DomainEventConsumer consumer = consumers.stream()
                .filter(candidate -> candidate.consumerKey().equals(delivery.getConsumerKey()))
                .findFirst()
                .orElse(null);
        if (consumer == null) {
            return;
        }

        delivery.setDeliveryStatus("processing");
        delivery.setAttempts(attempts(delivery) + 1);
        try {
            consumer.handle(event);
            delivery.setDeliveryStatus("delivered");
            delivery.setDeliveredAt(OffsetDateTime.now());
            delivery.setLastError(null);
            delivery.setNextAttemptAt(null);
        } catch (RuntimeException ex) {
            delivery.setDeliveryStatus("failed");
            delivery.setLastError(ex.getMessage());
            delivery.setNextAttemptAt(OffsetDateTime.now().plusMinutes(1));
        }
    }

    private void refreshEventStatus(DomainEvent event) {
        event.setAttempts(attempts(event) + 1);
        if (domainEventDeliveryRepository.countByDomainEventIdAndDeliveryStatusNot(event.getId(), "delivered") == 0) {
            event.setProcessingStatus("published");
            event.setPublishedAt(OffsetDateTime.now());
            event.setLastError(null);
            return;
        }
        if (domainEventDeliveryRepository.existsByDomainEventIdAndDeliveryStatus(event.getId(), "failed")) {
            event.setProcessingStatus("failed");
            event.setLastError("One or more event consumers failed");
            return;
        }
        event.setProcessingStatus("pending");
    }

    private int attempts(DomainEvent event) {
        return event.getAttempts() == null ? 0 : event.getAttempts();
    }

    private int attempts(DomainEventDelivery delivery) {
        return delivery.getAttempts() == null ? 0 : delivery.getAttempts();
    }
}
