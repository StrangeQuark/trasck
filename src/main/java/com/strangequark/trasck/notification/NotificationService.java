package com.strangequark.trasck.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public NotificationService(
            ObjectMapper objectMapper,
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            WorkspaceRepository workspaceRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.workspaceRepository = workspaceRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return notificationRepository.findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(workspaceId, actorId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID actorId = currentUserService.requireUserId();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, actorId)
                .orElseThrow(() -> notFound("Notification not found"));
        permissionService.requireWorkspacePermission(actorId, notification.getWorkspaceId(), "workspace.read");
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> listPreferences(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return notificationPreferenceRepository.findByWorkspaceIdAndUserIdOrderByChannelAscEventTypeAsc(workspaceId, actorId).stream()
                .map(NotificationPreferenceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> listDefaultPreferences(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        return notificationPreferenceRepository.findByWorkspaceIdAndUserIdIsNullOrderByChannelAscEventTypeAsc(workspaceId).stream()
                .map(NotificationPreferenceResponse::from)
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse upsertPreference(UUID workspaceId, NotificationPreferenceRequest request) {
        NotificationPreferenceRequest update = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        String channel = requiredText(update.channel(), "channel").toLowerCase();
        String eventType = requiredText(update.eventType(), "eventType");
        NotificationPreference preference = notificationPreferenceRepository
                .findByWorkspaceIdAndUserIdAndChannelAndEventType(workspaceId, actorId, channel, eventType)
                .orElseGet(NotificationPreference::new);
        boolean create = preference.getId() == null;
        if (create) {
            preference.setWorkspaceId(workspaceId);
            preference.setUserId(actorId);
        }
        applyPreference(preference, update, create);
        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        recordPreferenceEvent(saved, "notification.preference_saved", actorId);
        return NotificationPreferenceResponse.from(saved);
    }

    @Transactional
    public NotificationPreferenceResponse createDefaultPreference(UUID workspaceId, NotificationPreferenceRequest request) {
        NotificationPreferenceRequest update = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        String channel = requiredText(update.channel(), "channel").toLowerCase();
        String eventType = requiredText(update.eventType(), "eventType");
        NotificationPreference preference = notificationPreferenceRepository
                .findByWorkspaceIdAndUserIdIsNullAndChannelAndEventType(workspaceId, channel, eventType)
                .orElseGet(NotificationPreference::new);
        boolean create = preference.getId() == null;
        if (create) {
            preference.setWorkspaceId(workspaceId);
            preference.setUserId(null);
        }
        applyPreference(preference, update, create);
        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        recordPreferenceEvent(saved, "notification.default_preference_saved", actorId);
        return NotificationPreferenceResponse.from(saved);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID preferenceId, NotificationPreferenceRequest request) {
        NotificationPreferenceRequest update = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        NotificationPreference preference = notificationPreferenceRepository.findByIdAndUserId(preferenceId, actorId)
                .orElseThrow(() -> notFound("Notification preference not found"));
        permissionService.requireWorkspacePermission(actorId, preference.getWorkspaceId(), "workspace.read");
        applyPreference(preference, update, false);
        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        recordPreferenceEvent(saved, "notification.preference_updated", actorId);
        return NotificationPreferenceResponse.from(saved);
    }

    @Transactional
    public NotificationPreferenceResponse updateDefaultPreference(UUID preferenceId, NotificationPreferenceRequest request) {
        NotificationPreferenceRequest update = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        NotificationPreference preference = notificationPreferenceRepository.findByIdAndUserIdIsNull(preferenceId)
                .orElseThrow(() -> notFound("Notification default preference not found"));
        permissionService.requireWorkspacePermission(actorId, preference.getWorkspaceId(), "workspace.admin");
        applyPreference(preference, update, false);
        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        recordPreferenceEvent(saved, "notification.default_preference_updated", actorId);
        return NotificationPreferenceResponse.from(saved);
    }

    @Transactional
    public void deletePreference(UUID preferenceId) {
        UUID actorId = currentUserService.requireUserId();
        NotificationPreference preference = notificationPreferenceRepository.findByIdAndUserId(preferenceId, actorId)
                .orElseThrow(() -> notFound("Notification preference not found"));
        permissionService.requireWorkspacePermission(actorId, preference.getWorkspaceId(), "workspace.read");
        notificationPreferenceRepository.delete(preference);
        recordPreferenceEvent(preference, "notification.preference_deleted", actorId);
    }

    @Transactional
    public void deleteDefaultPreference(UUID preferenceId) {
        UUID actorId = currentUserService.requireUserId();
        NotificationPreference preference = notificationPreferenceRepository.findByIdAndUserIdIsNull(preferenceId)
                .orElseThrow(() -> notFound("Notification default preference not found"));
        permissionService.requireWorkspacePermission(actorId, preference.getWorkspaceId(), "workspace.admin");
        notificationPreferenceRepository.delete(preference);
        recordPreferenceEvent(preference, "notification.default_preference_deleted", actorId);
    }

    private void applyPreference(NotificationPreference preference, NotificationPreferenceRequest request, boolean create) {
        if (create || hasText(request.channel())) {
            preference.setChannel(requiredText(request.channel(), "channel").toLowerCase());
        }
        if (create || hasText(request.eventType())) {
            preference.setEventType(requiredText(request.eventType(), "eventType"));
        }
        if (request.enabled() != null) {
            preference.setEnabled(request.enabled());
        } else if (create) {
            preference.setEnabled(true);
        }
        if (create || request.config() != null) {
            preference.setConfig(toJsonObject(request.config()));
        }
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private JsonNode toJsonNullable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private JsonNode toJsonObject(Object value) {
        JsonNode json = toJsonNullable(value);
        if (json == null || json.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!json.isObject()) {
            throw badRequest("JSON value must be an object");
        }
        return json;
    }

    private void recordPreferenceEvent(NotificationPreference preference, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("preferenceId", preference.getId().toString())
                .put("scope", preference.getUserId() == null ? "workspace_default" : "user")
                .put("channel", preference.getChannel())
                .put("eventType", preference.getEventType())
                .put("actorUserId", actorId.toString());
        if (preference.getUserId() != null) {
            payload.put("userId", preference.getUserId().toString());
        }
        domainEventService.record(preference.getWorkspaceId(), "notification_preference", preference.getId(), eventType, payload);
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw badRequest(fieldName + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
