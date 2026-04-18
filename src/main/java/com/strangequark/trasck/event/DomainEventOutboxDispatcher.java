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
    private final EventConsumerConfigRepository eventConsumerConfigRepository;
    private final List<DomainEventConsumer> consumers;
    private final List<ConfiguredDomainEventConsumer> configuredConsumers;
    private final int batchSize;

    public DomainEventOutboxDispatcher(
            DomainEventRepository domainEventRepository,
            DomainEventDeliveryRepository domainEventDeliveryRepository,
            EventConsumerConfigRepository eventConsumerConfigRepository,
            List<DomainEventConsumer> consumers,
            List<ConfiguredDomainEventConsumer> configuredConsumers,
            @Value("${trasck.events.outbox.batch-size:50}") int batchSize
    ) {
        this.domainEventRepository = domainEventRepository;
        this.domainEventDeliveryRepository = domainEventDeliveryRepository;
        this.eventConsumerConfigRepository = eventConsumerConfigRepository;
        this.consumers = consumers;
        this.configuredConsumers = configuredConsumers;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${trasck.events.outbox.fixed-delay-ms:5000}")
    @Transactional
    public void dispatchPending() {
        List<EventConsumerConfig> configs = eventConsumerConfigRepository.findByEnabledTrue();
        if (consumers.isEmpty() && configs.isEmpty()) {
            return;
        }
        for (DomainEvent event : domainEventRepository.findByProcessingStatusInOrderByOccurredAtAsc(
                List.of("pending", "failed"),
                PageRequest.of(0, batchSize)
        )) {
            dispatch(event, matchingConfigs(event, configs));
        }
    }

    private void dispatch(DomainEvent event, List<EventConsumerConfig> configs) {
        ensureDeliveries(event, configs);
        List<DomainEventDelivery> deliveries = domainEventDeliveryRepository.findByDomainEventIdAndDeliveryStatusIn(
                event.getId(),
                List.of("pending", "failed")
        );
        for (DomainEventDelivery delivery : deliveries) {
            deliver(event, delivery, configs);
        }
        refreshEventStatus(event);
    }

    private void ensureDeliveries(DomainEvent event, List<EventConsumerConfig> configs) {
        for (DomainEventConsumer consumer : consumers) {
            ensureDelivery(event, consumer.consumerKey());
        }
        for (EventConsumerConfig config : configs) {
            ensureDelivery(event, config.getConsumerKey());
        }
    }

    private DomainEventDelivery ensureDelivery(DomainEvent event, String consumerKey) {
        return domainEventDeliveryRepository.findByDomainEventIdAndConsumerKey(event.getId(), consumerKey)
                .orElseGet(() -> {
                    DomainEventDelivery delivery = new DomainEventDelivery();
                    delivery.setDomainEventId(event.getId());
                    delivery.setConsumerKey(consumerKey);
                    delivery.setDeliveryStatus("pending");
                    delivery.setAttempts(0);
                    return domainEventDeliveryRepository.save(delivery);
                });
    }

    private void deliver(DomainEvent event, DomainEventDelivery delivery, List<EventConsumerConfig> configs) {
        DomainEventConsumer consumer = consumers.stream()
                .filter(candidate -> candidate.consumerKey().equals(delivery.getConsumerKey()))
                .findFirst()
                .orElse(null);
        EventConsumerConfig config = consumer == null ? configuredConsumerConfig(delivery.getConsumerKey(), configs) : null;
        if (consumer == null && config == null) {
            return;
        }

        delivery.setDeliveryStatus("processing");
        delivery.setAttempts(attempts(delivery) + 1);
        try {
            if (consumer != null) {
                consumer.handle(event);
            } else {
                configuredConsumer(config).handle(event, config);
            }
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

    private List<EventConsumerConfig> matchingConfigs(DomainEvent event, List<EventConsumerConfig> configs) {
        return configs.stream()
                .filter(config -> workspaceMatches(event, config))
                .filter(config -> eventTypeMatches(event, config))
                .toList();
    }

    private boolean workspaceMatches(DomainEvent event, EventConsumerConfig config) {
        return config.getWorkspaceId() == null || config.getWorkspaceId().equals(event.getWorkspaceId());
    }

    private boolean eventTypeMatches(DomainEvent event, EventConsumerConfig config) {
        if (config.getEventTypes() == null || !config.getEventTypes().isArray() || config.getEventTypes().isEmpty()) {
            return true;
        }
        for (int i = 0; i < config.getEventTypes().size(); i++) {
            String eventType = config.getEventTypes().get(i).asText();
            if ("*".equals(eventType) || event.getEventType().equals(eventType)) {
                return true;
            }
        }
        return false;
    }

    private EventConsumerConfig configuredConsumerConfig(String consumerKey, List<EventConsumerConfig> configs) {
        return configs.stream()
                .filter(config -> config.getConsumerKey().equals(consumerKey))
                .findFirst()
                .orElse(null);
    }

    private ConfiguredDomainEventConsumer configuredConsumer(EventConsumerConfig config) {
        return configuredConsumers.stream()
                .filter(consumer -> consumer.consumerType().equals(config.getConsumerType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No configured event consumer handler for type " + config.getConsumerType()));
    }

    private int attempts(DomainEvent event) {
        return event.getAttempts() == null ? 0 : event.getAttempts();
    }

    private int attempts(DomainEventDelivery delivery) {
        return delivery.getAttempts() == null ? 0 : delivery.getAttempts();
    }
}
