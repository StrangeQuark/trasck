package com.strangequark.trasck.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.event.DomainEvent;
import com.strangequark.trasck.event.DomainEventConsumer;
import com.strangequark.trasck.event.DomainEventPayloadRedactor;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuditProjectionConsumer implements DomainEventConsumer {

    public static final String CONSUMER_KEY = "audit-projection";

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final ObjectMapper objectMapper;
    private final DomainEventPayloadRedactor payloadRedactor;

    public AuditProjectionConsumer(
            AuditLogEntryRepository auditLogEntryRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            ObjectMapper objectMapper,
            DomainEventPayloadRedactor payloadRedactor
    ) {
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.objectMapper = objectMapper;
        this.payloadRedactor = payloadRedactor;
    }

    @Override
    public String consumerKey() {
        return CONSUMER_KEY;
    }

    @Override
    public void handle(DomainEvent event) {
        if (!isAuditEvent(event)) {
            return;
        }
        UUID actorId = firstUuid(event.getPayload(), "actorUserId", "createdById", "invitedById", "revokedById", "userId");
        for (UUID workspaceId : auditWorkspaces(event)) {
            createIfMissing(event, workspaceId, actorId);
        }
    }

    private boolean isAuditEvent(DomainEvent event) {
        String eventType = event.getEventType();
        return eventType.equals("auth.login")
                || eventType.equals("auth.oauth_login")
                || eventType.startsWith("auth.user_")
                || eventType.equals("auth.oauth_identity_linked")
                || eventType.equals("auth.api_token_created")
                || eventType.equals("auth.api_token_revoked")
                || eventType.equals("auth.service_token_created")
                || eventType.equals("auth.service_token_revoked")
                || eventType.equals("audit.retention_policy_updated")
                || eventType.equals("event.replay_requested")
                || eventType.startsWith("agent.provider.")
                || eventType.startsWith("agent.profile.")
                || eventType.startsWith("agent.task.")
                || eventType.startsWith("repository_connection.");
    }

    private Set<UUID> auditWorkspaces(DomainEvent event) {
        Set<UUID> workspaceIds = new LinkedHashSet<>();
        if (event.getWorkspaceId() != null) {
            workspaceIds.add(event.getWorkspaceId());
        }
        UUID userId = firstUuid(event.getPayload(), "userId", "actorUserId");
        if (workspaceIds.isEmpty() && userId != null) {
            workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(userId, "active")
                    .forEach(membership -> workspaceIds.add(membership.getWorkspaceId()));
        }
        return workspaceIds;
    }

    private void createIfMissing(DomainEvent event, UUID workspaceId, UUID actorId) {
        if (auditLogEntryRepository.existsByDomainEventIdAndWorkspaceIdAndAction(event.getId(), workspaceId, event.getEventType())) {
            return;
        }
        AuditLogEntry entry = new AuditLogEntry();
        entry.setDomainEventId(event.getId());
        entry.setWorkspaceId(workspaceId);
        entry.setActorId(actorId);
        entry.setAction(event.getEventType());
        entry.setTargetType(event.getAggregateType());
        entry.setTargetId(event.getAggregateId());
        entry.setBeforeValue(redactedChild(event.getPayload(), "before"));
        entry.setAfterValue(afterValue(event));
        entry.setCreatedAt(event.getOccurredAt());
        auditLogEntryRepository.save(entry);
    }

    private JsonNode redactedChild(JsonNode payload, String fieldName) {
        if (payload == null || payload.get(fieldName) == null || payload.get(fieldName).isNull()) {
            return null;
        }
        return payloadRedactor.redact(payload.get(fieldName));
    }

    private ObjectNode afterValue(DomainEvent event) {
        ObjectNode value = objectMapper.createObjectNode()
                .put("domainEventId", event.getId().toString())
                .put("aggregateType", event.getAggregateType())
                .put("aggregateId", event.getAggregateId().toString())
                .put("eventType", event.getEventType());
        JsonNode payload = event.getPayload();
        if (payload != null && payload.get("after") != null) {
            value.set("payload", payloadRedactor.redact(payload.get("after")));
        } else {
            value.set("payload", payloadRedactor.redact(payload));
        }
        return value;
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
