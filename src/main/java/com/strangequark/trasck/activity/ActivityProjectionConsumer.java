package com.strangequark.trasck.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEvent;
import com.strangequark.trasck.event.DomainEventConsumer;
import com.strangequark.trasck.event.DomainEventPayloadRedactor;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ActivityProjectionConsumer implements DomainEventConsumer {

    public static final String CONSUMER_KEY = "activity-projection";

    private final ActivityEventRepository activityEventRepository;
    private final ObjectMapper objectMapper;
    private final DomainEventPayloadRedactor payloadRedactor;

    public ActivityProjectionConsumer(
            ActivityEventRepository activityEventRepository,
            ObjectMapper objectMapper,
            DomainEventPayloadRedactor payloadRedactor
    ) {
        this.activityEventRepository = activityEventRepository;
        this.objectMapper = objectMapper;
        this.payloadRedactor = payloadRedactor;
    }

    @Override
    public String consumerKey() {
        return CONSUMER_KEY;
    }

    @Override
    public void handle(DomainEvent event) {
        if (event.getWorkspaceId() == null || !isActivityEvent(event)) {
            return;
        }
        UUID actorId = firstUuid(event.getPayload(), "actorUserId", "createdById", "invitedById", "revokedById", "userId");
        ObjectNode metadata = metadata(event);
        if ("work_item".equals(event.getAggregateType())) {
            createIfMissing(event, actorId, "work_item", event.getAggregateId(), metadata);
            createIfMissing(event, actorId, "project", uuid(event.getPayload(), "projectId"), metadata);
            createIfMissing(event, actorId, "workspace", event.getWorkspaceId(), metadata);
            return;
        }
        createIfMissing(event, actorId, event.getAggregateType(), event.getAggregateId(), metadata);
        createIfMissing(event, actorId, "workspace", event.getWorkspaceId(), metadata);
    }

    private boolean isActivityEvent(DomainEvent event) {
        String eventType = event.getEventType();
        return eventType.startsWith("work_item.")
                || eventType.equals("label.created")
                || eventType.startsWith("auth.user_")
                || eventType.equals("auth.service_token_created")
                || eventType.equals("auth.service_token_revoked")
                || eventType.equals("audit.retention_policy_updated")
                || eventType.equals("event.replay_requested");
    }

    private void createIfMissing(DomainEvent event, UUID actorId, String entityType, UUID entityId, JsonNode metadata) {
        if (entityId == null || activityEventRepository.existsByDomainEventIdAndEntityTypeAndEntityId(event.getId(), entityType, entityId)) {
            return;
        }
        ActivityEvent activity = new ActivityEvent();
        activity.setDomainEventId(event.getId());
        activity.setWorkspaceId(event.getWorkspaceId());
        activity.setActorId(actorId);
        activity.setEntityType(entityType);
        activity.setEntityId(entityId);
        activity.setEventType(event.getEventType());
        activity.setMetadata(metadata.deepCopy());
        activity.setCreatedAt(event.getOccurredAt());
        activityEventRepository.save(activity);
    }

    private ObjectNode metadata(DomainEvent event) {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("domainEventId", event.getId().toString())
                .put("aggregateType", event.getAggregateType())
                .put("aggregateId", event.getAggregateId().toString())
                .put("eventType", event.getEventType());
        metadata.set("payload", payloadRedactor.redact(event.getPayload()));
        return metadata;
    }

    private UUID firstUuid(JsonNode payload, String... keys) {
        for (String key : keys) {
            UUID value = uuid(payload, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private UUID uuid(JsonNode payload, String key) {
        if (payload == null || payload.get(key) == null || payload.get(key).asText().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(payload.get(key).asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
