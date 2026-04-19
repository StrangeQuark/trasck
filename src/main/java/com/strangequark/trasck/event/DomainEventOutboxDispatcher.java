package com.strangequark.trasck.event;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
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
            dispatch(event, matchingConfigs(event, configs), null);
        }
    }

    @Transactional
    public DomainEventReplayResult replayWorkspace(
            UUID workspaceId,
            List<UUID> eventIds,
            List<String> consumerKeys,
            boolean includePublished
    ) {
        List<DomainEvent> events = replayEvents(workspaceId, eventIds, includePublished);
        List<String> requestedConsumers = consumerKeys == null || consumerKeys.isEmpty()
                ? List.of("activity-projection", "audit-projection")
                : consumerKeys.stream().filter(key -> key != null && !key.isBlank()).map(String::trim).distinct().toList();
        List<EventConsumerConfig> configs = eventConsumerConfigRepository.findByEnabledTrue();
        int deliveriesReset = 0;
        for (DomainEvent event : events) {
            List<EventConsumerConfig> matchingConfigs = matchingConfigs(event, configs);
            boolean hasReplayConsumer = false;
            for (String consumerKey : requestedConsumers) {
                if (canHandle(consumerKey, matchingConfigs)) {
                    DomainEventDelivery delivery = ensureDelivery(event, consumerKey);
                    resetDelivery(delivery);
                    deliveriesReset++;
                    hasReplayConsumer = true;
                }
            }
            if (hasReplayConsumer) {
                dispatch(event, matchingConfigs, requestedConsumers);
            }
        }
        return new DomainEventReplayResult(events.size(), deliveriesReset);
    }

    private void dispatch(DomainEvent event, List<EventConsumerConfig> configs, Collection<String> selectedConsumerKeys) {
        ensureDeliveries(event, configs, selectedConsumerKeys);
        List<DomainEventDelivery> deliveries = domainEventDeliveryRepository.findByDomainEventIdAndDeliveryStatusIn(
                event.getId(),
                List.of("pending", "failed")
        ).stream()
                .filter(delivery -> selectedConsumerKeys == null || selectedConsumerKeys.contains(delivery.getConsumerKey()))
                .filter(this::readyForAttempt)
                .toList();
        for (DomainEventDelivery delivery : deliveries) {
            deliver(event, delivery, configs);
        }
        refreshEventStatus(event);
    }

    private void ensureDeliveries(DomainEvent event, List<EventConsumerConfig> configs, Collection<String> selectedConsumerKeys) {
        if (selectedConsumerKeys == null) {
            for (DomainEventConsumer consumer : consumers) {
                ensureDelivery(event, consumer.consumerKey());
            }
            for (EventConsumerConfig config : configs) {
                ensureDelivery(event, config.getConsumerKey());
            }
            return;
        }
        for (String consumerKey : selectedConsumerKeys) {
            if (canHandle(consumerKey, configs)) {
                ensureDelivery(event, consumerKey);
            }
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

    private void resetDelivery(DomainEventDelivery delivery) {
        delivery.setDeliveryStatus("pending");
        delivery.setLastError(null);
        delivery.setNextAttemptAt(null);
        delivery.setDeliveredAt(null);
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
            delivery.setLastError(ex.getMessage());
            if (shouldDeadLetter(delivery, config)) {
                delivery.setDeliveryStatus("dead_lettered");
                delivery.setNextAttemptAt(null);
            } else {
                delivery.setDeliveryStatus("failed");
                delivery.setNextAttemptAt(OffsetDateTime.now().plusMinutes(1));
            }
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
        if (domainEventDeliveryRepository.existsByDomainEventIdAndDeliveryStatus(event.getId(), "dead_lettered")) {
            event.setProcessingStatus("dead_lettered");
            event.setLastError("One or more event consumers were dead-lettered");
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

    private boolean canHandle(String consumerKey, List<EventConsumerConfig> configs) {
        return consumers.stream().anyMatch(consumer -> consumer.consumerKey().equals(consumerKey))
                || configs.stream().anyMatch(config -> config.getConsumerKey().equals(consumerKey));
    }

    private List<DomainEvent> replayEvents(UUID workspaceId, List<UUID> eventIds, boolean includePublished) {
        if (eventIds != null && !eventIds.isEmpty()) {
            return domainEventRepository.findByWorkspaceIdAndIdInOrderByOccurredAtAsc(workspaceId, eventIds);
        }
        if (includePublished) {
            return domainEventRepository.findByWorkspaceIdOrderByOccurredAtAsc(workspaceId);
        }
        return domainEventRepository.findByWorkspaceIdAndProcessingStatusInOrderByOccurredAtAsc(workspaceId, List.of("pending", "failed"));
    }

    private ConfiguredDomainEventConsumer configuredConsumer(EventConsumerConfig config) {
        return configuredConsumers.stream()
                .filter(consumer -> consumer.consumerType().equals(config.getConsumerType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No configured event consumer handler for type " + config.getConsumerType()));
    }

    private boolean readyForAttempt(DomainEventDelivery delivery) {
        return delivery.getNextAttemptAt() == null || !delivery.getNextAttemptAt().isAfter(OffsetDateTime.now());
    }

    private boolean shouldDeadLetter(DomainEventDelivery delivery, EventConsumerConfig config) {
        int maxAttempts = maxAttempts(config);
        return maxAttempts > 0 && attempts(delivery) >= maxAttempts && deadLetterOnExhaustion(config);
    }

    private int maxAttempts(EventConsumerConfig config) {
        if (config == null || config.getConfig() == null || !config.getConfig().hasNonNull("maxAttempts")) {
            return 0;
        }
        return Math.max(0, config.getConfig().path("maxAttempts").asInt(0));
    }

    private boolean deadLetterOnExhaustion(EventConsumerConfig config) {
        return config != null
                && config.getConfig() != null
                && config.getConfig().path("deadLetterOnExhaustion").asBoolean(false);
    }

    private int attempts(DomainEvent event) {
        return event.getAttempts() == null ? 0 : event.getAttempts();
    }

    private int attempts(DomainEventDelivery delivery) {
        return delivery.getAttempts() == null ? 0 : delivery.getAttempts();
    }
}
