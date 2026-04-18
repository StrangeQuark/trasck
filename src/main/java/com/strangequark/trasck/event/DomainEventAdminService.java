package com.strangequark.trasck.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DomainEventAdminService {

    private static final List<String> DEFAULT_REPLAY_CONSUMERS = List.of("activity-projection", "audit-projection");

    private final DomainEventOutboxDispatcher outboxDispatcher;
    private final DomainEventService domainEventService;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public DomainEventAdminService(
            DomainEventOutboxDispatcher outboxDispatcher,
            DomainEventService domainEventService,
            WorkspaceRepository workspaceRepository,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.outboxDispatcher = outboxDispatcher;
        this.domainEventService = domainEventService;
        this.workspaceRepository = workspaceRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DomainEventReplayResponse replay(UUID workspaceId, DomainEventReplayRequest request) {
        activeWorkspace(workspaceId);
        DomainEventReplayRequest replayRequest = request == null ? new DomainEventReplayRequest(null, null, true) : request;
        List<String> consumerKeys = replayRequest.consumerKeys() == null || replayRequest.consumerKeys().isEmpty()
                ? DEFAULT_REPLAY_CONSUMERS
                : replayRequest.consumerKeys().stream().filter(key -> key != null && !key.isBlank()).map(String::trim).distinct().toList();
        boolean includePublished = replayRequest.includePublished() == null || Boolean.TRUE.equals(replayRequest.includePublished());
        UUID actorId = currentUserService.requireUserId();
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("actorUserId", actorId.toString())
                .put("includePublished", includePublished);
        ArrayNode consumers = objectMapper.createArrayNode();
        consumerKeys.forEach(consumers::add);
        payload.set("consumerKeys", consumers);
        if (replayRequest.eventIds() != null && !replayRequest.eventIds().isEmpty()) {
            ArrayNode eventIds = objectMapper.createArrayNode();
            replayRequest.eventIds().forEach(id -> eventIds.add(id.toString()));
            payload.set("eventIds", eventIds);
        }
        domainEventService.record(workspaceId, "workspace", workspaceId, "event.replay_requested", payload);
        DomainEventReplayResult result = outboxDispatcher.replayWorkspace(
                workspaceId,
                replayRequest.eventIds(),
                consumerKeys,
                includePublished
        );
        return new DomainEventReplayResponse(workspaceId, consumerKeys, includePublished, result.eventsMatched(), result.deliveriesReset());
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return workspace;
    }
}
