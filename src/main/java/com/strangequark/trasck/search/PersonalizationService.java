package com.strangequark.trasck.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalizationService {

    private final ObjectMapper objectMapper;
    private final SavedViewRepository savedViewRepository;
    private final FavoriteRepository favoriteRepository;
    private final RecentItemRepository recentItemRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;
    private final JdbcTemplate jdbcTemplate;

    public PersonalizationService(
            ObjectMapper objectMapper,
            SavedViewRepository savedViewRepository,
            FavoriteRepository favoriteRepository,
            RecentItemRepository recentItemRepository,
            WorkspaceRepository workspaceRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectMapper = objectMapper;
        this.savedViewRepository = savedViewRepository;
        this.favoriteRepository = favoriteRepository;
        this.recentItemRepository = recentItemRepository;
        this.workspaceRepository = workspaceRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<SavedViewResponse> listViews(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return savedViewRepository.findVisibleCandidates(workspaceId, actorId).stream()
                .filter(view -> canReadView(actorId, view))
                .map(SavedViewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedViewResponse getView(UUID viewId) {
        UUID actorId = currentUserService.requireUserId();
        SavedView view = savedView(viewId);
        activeWorkspace(view.getWorkspaceId());
        requireReadableView(actorId, view);
        return SavedViewResponse.from(view);
    }

    @Transactional
    public SavedViewResponse createView(UUID workspaceId, SavedViewRequest request) {
        SavedViewRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        String visibility = normalizeVisibility(createRequest.visibility());
        requireViewCreatePermission(actorId, workspaceId, visibility);
        SavedView view = new SavedView();
        view.setWorkspaceId(workspaceId);
        view.setOwnerId(actorId);
        view.setName(requiredText(createRequest.name(), "name"));
        view.setViewType(requiredText(createRequest.viewType(), "viewType").toLowerCase());
        view.setConfig(validatedConfig(createRequest.config()));
        view.setVisibility(visibility);
        SavedView saved = savedViewRepository.save(view);
        recordViewEvent(saved, "view.created", actorId);
        return SavedViewResponse.from(saved);
    }

    @Transactional
    public SavedViewResponse updateView(UUID viewId, SavedViewRequest request) {
        SavedViewRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        SavedView view = savedView(viewId);
        activeWorkspace(view.getWorkspaceId());
        requireWritableView(actorId, view);
        String targetVisibility = hasText(updateRequest.visibility())
                ? normalizeVisibility(updateRequest.visibility())
                : view.getVisibility();
        requireViewCreatePermission(actorId, view.getWorkspaceId(), targetVisibility);
        if (hasText(updateRequest.name())) {
            view.setName(updateRequest.name().trim());
        }
        if (hasText(updateRequest.viewType())) {
            view.setViewType(updateRequest.viewType().trim().toLowerCase());
        }
        if (updateRequest.config() != null) {
            view.setConfig(validatedConfig(updateRequest.config()));
        }
        view.setVisibility(targetVisibility);
        SavedView saved = savedViewRepository.save(view);
        recordViewEvent(saved, "view.updated", actorId);
        return SavedViewResponse.from(saved);
    }

    @Transactional
    public void deleteView(UUID viewId) {
        UUID actorId = currentUserService.requireUserId();
        SavedView view = savedView(viewId);
        activeWorkspace(view.getWorkspaceId());
        requireWritableView(actorId, view);
        savedViewRepository.delete(view);
        recordViewEvent(view, "view.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> listFavorites(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(actorId).stream()
                .filter(favorite -> entityWorkspaceId(favorite.getEntityType(), favorite.getEntityId()).map(workspaceId::equals).orElse(false))
                .map(FavoriteResponse::from)
                .toList();
    }

    @Transactional
    public FavoriteResponse addFavorite(UUID workspaceId, FavoriteRequest request) {
        FavoriteRequest favoriteRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        String entityType = normalizeEntityType(favoriteRequest.entityType());
        UUID entityId = required(favoriteRequest.entityId(), "entityId");
        assertEntityInWorkspace(workspaceId, entityType, entityId);
        Favorite favorite = favoriteRepository.findByUserIdAndEntityTypeAndEntityId(actorId, entityType, entityId)
                .orElseGet(() -> {
                    Favorite created = new Favorite();
                    created.setUserId(actorId);
                    created.setEntityType(entityType);
                    created.setEntityId(entityId);
                    created.setCreatedAt(OffsetDateTime.now());
                    return created;
                });
        Favorite saved = favoriteRepository.save(favorite);
        recordEntityEvent(workspaceId, "favorite", saved.getId(), "favorite.created", actorId, entityType, entityId);
        return FavoriteResponse.from(saved);
    }

    @Transactional
    public void deleteFavorite(UUID favoriteId) {
        UUID actorId = currentUserService.requireUserId();
        Favorite favorite = favoriteRepository.findById(favoriteId).orElseThrow(() -> notFound("Favorite not found"));
        if (!actorId.equals(favorite.getUserId())) {
            throw notFound("Favorite not found");
        }
        Optional<UUID> workspaceId = entityWorkspaceId(favorite.getEntityType(), favorite.getEntityId());
        favoriteRepository.delete(favorite);
        workspaceId.ifPresent(id -> recordEntityEvent(id, "favorite", favorite.getId(), "favorite.deleted", actorId, favorite.getEntityType(), favorite.getEntityId()));
    }

    @Transactional(readOnly = true)
    public List<RecentItemResponse> listRecentItems(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return recentItemRepository.findByUserIdOrderByViewedAtDesc(actorId).stream()
                .filter(recent -> entityWorkspaceId(recent.getEntityType(), recent.getEntityId()).map(workspaceId::equals).orElse(false))
                .map(RecentItemResponse::from)
                .toList();
    }

    @Transactional
    public RecentItemResponse recordRecentItem(UUID workspaceId, RecentItemRequest request) {
        RecentItemRequest recentRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        String entityType = normalizeEntityType(recentRequest.entityType());
        UUID entityId = required(recentRequest.entityId(), "entityId");
        assertEntityInWorkspace(workspaceId, entityType, entityId);
        RecentItem recentItem = recentItemRepository.findByUserIdAndEntityTypeAndEntityId(actorId, entityType, entityId)
                .orElseGet(() -> {
                    RecentItem created = new RecentItem();
                    created.setUserId(actorId);
                    created.setEntityType(entityType);
                    created.setEntityId(entityId);
                    return created;
                });
        recentItem.setViewedAt(OffsetDateTime.now());
        RecentItem saved = recentItemRepository.save(recentItem);
        recordEntityEvent(workspaceId, "recent_item", saved.getId(), "recent_item.recorded", actorId, entityType, entityId);
        return RecentItemResponse.from(saved);
    }

    @Transactional
    public void deleteRecentItem(UUID recentItemId) {
        UUID actorId = currentUserService.requireUserId();
        RecentItem recentItem = recentItemRepository.findById(recentItemId).orElseThrow(() -> notFound("Recent item not found"));
        if (!actorId.equals(recentItem.getUserId())) {
            throw notFound("Recent item not found");
        }
        Optional<UUID> workspaceId = entityWorkspaceId(recentItem.getEntityType(), recentItem.getEntityId());
        recentItemRepository.delete(recentItem);
        workspaceId.ifPresent(id -> recordEntityEvent(id, "recent_item", recentItem.getId(), "recent_item.deleted", actorId, recentItem.getEntityType(), recentItem.getEntityId()));
    }

    private void requireReadableView(UUID actorId, SavedView view) {
        if (!canReadView(actorId, view)) {
            throw notFound("Saved view not found");
        }
    }

    private boolean canReadView(UUID actorId, SavedView view) {
        if (actorId.equals(view.getOwnerId())) {
            return permissionService.canUseWorkspace(actorId, view.getWorkspaceId(), "workspace.read");
        }
        return ("workspace".equals(view.getVisibility()) || "public".equals(view.getVisibility()))
                && permissionService.canUseWorkspace(actorId, view.getWorkspaceId(), "workspace.read");
    }

    private void requireWritableView(UUID actorId, SavedView view) {
        if ("private".equals(view.getVisibility())
                && actorId.equals(view.getOwnerId())
                && permissionService.canUseWorkspace(actorId, view.getWorkspaceId(), "workspace.read")) {
            return;
        }
        permissionService.requireWorkspacePermission(actorId, view.getWorkspaceId(), "workspace.admin");
    }

    private void requireViewCreatePermission(UUID actorId, UUID workspaceId, String visibility) {
        if ("private".equals(visibility)) {
            permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
            return;
        }
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
    }

    private void assertEntityInWorkspace(UUID workspaceId, String entityType, UUID entityId) {
        UUID entityWorkspaceId = entityWorkspaceId(entityType, entityId).orElseThrow(() -> notFound("Personalization target not found"));
        if (!workspaceId.equals(entityWorkspaceId)) {
            throw notFound("Personalization target not found");
        }
    }

    private Optional<UUID> entityWorkspaceId(String entityType, UUID entityId) {
        return switch (entityType) {
            case "workspace" -> workspaceRepository.findById(entityId)
                    .filter(workspace -> workspace.getDeletedAt() == null && "active".equals(workspace.getStatus()))
                    .map(Workspace::getId);
            case "project" -> queryWorkspaceId("""
                    select workspace_id
                    from projects
                    where id = ?
                      and deleted_at is null
                      and status = 'active'
                    """, entityId);
            case "work_item" -> queryWorkspaceId("""
                    select workspace_id
                    from work_items
                    where id = ?
                      and deleted_at is null
                    """, entityId);
            case "dashboard" -> queryWorkspaceId("select workspace_id from dashboards where id = ?", entityId);
            case "saved_filter" -> queryWorkspaceId("select workspace_id from saved_filters where id = ?", entityId);
            case "report_query" -> queryWorkspaceId("select workspace_id from report_query_catalog where id = ? and enabled = true", entityId);
            case "view" -> queryWorkspaceId("select workspace_id from views where id = ?", entityId);
            case "team" -> queryWorkspaceId("select workspace_id from teams where id = ? and status = 'active'", entityId);
            case "iteration" -> queryWorkspaceId("select workspace_id from iterations where id = ?", entityId);
            case "agent_task" -> queryWorkspaceId("select workspace_id from agent_tasks where id = ?", entityId);
            default -> Optional.empty();
        };
    }

    private Optional<UUID> queryWorkspaceId(String sql, UUID entityId) {
        List<UUID> workspaceIds = jdbcTemplate.query(
                sql,
                ps -> ps.setObject(1, entityId),
                (rs, rowNum) -> (UUID) rs.getObject("workspace_id")
        );
        return workspaceIds.stream().findFirst();
    }

    private SavedView savedView(UUID viewId) {
        return savedViewRepository.findById(viewId).orElseThrow(() -> notFound("Saved view not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private JsonNode validatedConfig(Object value) {
        JsonNode json = toJsonObject(value);
        if (containsRawSqlKey(json)) {
            throw badRequest("saved views must not contain raw SQL");
        }
        return json;
    }

    private boolean containsRawSqlKey(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("sql".equalsIgnoreCase(field.getKey()) || "rawSql".equalsIgnoreCase(field.getKey())) {
                    return true;
                }
                if (containsRawSqlKey(field.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsRawSqlKey(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode toJsonObject(Object value) {
        JsonNode json;
        if (value == null) {
            json = objectMapper.createObjectNode();
        } else if (value instanceof JsonNode jsonNode) {
            json = jsonNode;
        } else {
            json = objectMapper.valueToTree(value);
        }
        if (!json.isObject()) {
            throw badRequest("config must be a JSON object");
        }
        return json;
    }

    private String normalizeVisibility(String visibility) {
        String normalized = hasText(visibility) ? visibility.trim().toLowerCase() : "private";
        if (!List.of("private", "workspace", "public").contains(normalized)) {
            throw badRequest("visibility must be private, workspace, or public");
        }
        return normalized;
    }

    private String normalizeEntityType(String entityType) {
        String normalized = requiredText(entityType, "entityType").toLowerCase();
        if (!List.of("workspace", "project", "work_item", "dashboard", "saved_filter", "report_query", "view", "team", "iteration", "agent_task").contains(normalized)) {
            throw badRequest("Unsupported personalization entityType");
        }
        return normalized;
    }

    private void recordViewEvent(SavedView view, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("viewId", view.getId().toString())
                .put("viewType", view.getViewType())
                .put("visibility", view.getVisibility())
                .put("actorUserId", actorId.toString());
        domainEventService.record(view.getWorkspaceId(), "view", view.getId(), eventType, payload);
    }

    private void recordEntityEvent(UUID workspaceId, String aggregateType, UUID aggregateId, String eventType, UUID actorId, String entityType, UUID entityId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("actorUserId", actorId.toString())
                .put("entityType", entityType)
                .put("entityId", entityId.toString());
        domainEventService.record(workspaceId, aggregateType, aggregateId, eventType, payload);
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
