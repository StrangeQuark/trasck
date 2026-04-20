package com.strangequark.trasck.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamMembershipRepository;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedFilterService {

    private final ObjectMapper objectMapper;
    private final SavedFilterRepository savedFilterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public SavedFilterService(
            ObjectMapper objectMapper,
            SavedFilterRepository savedFilterRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMembershipRepository teamMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.savedFilterRepository = savedFilterRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<SavedFilterResponse> list(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.read");
        boolean canManageWorkspaceReports = canManageReports(actorId, workspaceId);
        return savedFilterRepository.findVisibleCandidates(workspaceId, actorId).stream()
                .filter(savedFilter -> canRead(actorId, savedFilter, canManageWorkspaceReports))
                .map(SavedFilterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavedFilterResponse> listByProject(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "report.read");
        boolean canManageWorkspaceReports = canManageReports(actorId, project.getWorkspaceId());
        return savedFilterRepository.findByWorkspaceIdAndVisibilityAndProjectIdOrderByNameAsc(project.getWorkspaceId(), "project", project.getId()).stream()
                .filter(savedFilter -> canRead(actorId, savedFilter, canManageWorkspaceReports))
                .map(SavedFilterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavedFilterResponse> listByTeam(UUID teamId) {
        UUID actorId = currentUserService.requireUserId();
        Team team = activeTeam(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "report.read");
        boolean canManageWorkspaceReports = canManageReports(actorId, team.getWorkspaceId());
        return savedFilterRepository.findByWorkspaceIdAndVisibilityAndTeamIdOrderByNameAsc(team.getWorkspaceId(), "team", team.getId()).stream()
                .filter(savedFilter -> canRead(actorId, savedFilter, canManageWorkspaceReports))
                .map(SavedFilterResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedFilterResponse get(UUID savedFilterId) {
        UUID actorId = currentUserService.requireUserId();
        SavedFilter savedFilter = savedFilter(savedFilterId);
        activeWorkspace(savedFilter.getWorkspaceId());
        requireReadable(actorId, savedFilter);
        return SavedFilterResponse.from(savedFilter);
    }

    @Transactional
    public SavedFilterResponse create(UUID workspaceId, SavedFilterRequest request) {
        SavedFilterRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        String visibility = normalizeVisibility(createRequest.visibility());
        UUID projectId = normalizeProjectId(workspaceId, visibility, createRequest.projectId());
        UUID teamId = normalizeTeamId(workspaceId, visibility, createRequest.teamId());
        requireSharedPermission(actorId, workspaceId, visibility, projectId);

        SavedFilter savedFilter = new SavedFilter();
        savedFilter.setWorkspaceId(workspaceId);
        savedFilter.setOwnerId(actorId);
        savedFilter.setProjectId(projectId);
        savedFilter.setTeamId(teamId);
        savedFilter.setName(requiredText(createRequest.name(), "name"));
        savedFilter.setVisibility(visibility);
        savedFilter.setQuery(validatedQuery(workspaceId, createRequest.query()));
        SavedFilter saved = savedFilterRepository.save(savedFilter);
        recordEvent(saved, "saved_filter.created", actorId);
        return SavedFilterResponse.from(saved);
    }

    @Transactional
    public SavedFilterResponse update(UUID savedFilterId, SavedFilterRequest request) {
        SavedFilterRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        SavedFilter savedFilter = savedFilter(savedFilterId);
        activeWorkspace(savedFilter.getWorkspaceId());
        requireWritable(actorId, savedFilter);
        String targetVisibility = hasText(updateRequest.visibility())
                ? normalizeVisibility(updateRequest.visibility())
                : savedFilter.getVisibility();
        UUID requestedProjectId = updateRequest.projectId();
        if (requestedProjectId == null && "project".equals(targetVisibility) && "project".equals(savedFilter.getVisibility())) {
            requestedProjectId = savedFilter.getProjectId();
        }
        UUID requestedTeamId = updateRequest.teamId();
        if (requestedTeamId == null && "team".equals(targetVisibility) && "team".equals(savedFilter.getVisibility())) {
            requestedTeamId = savedFilter.getTeamId();
        }
        UUID targetProjectId = hasText(updateRequest.visibility()) || updateRequest.projectId() != null
                ? normalizeProjectId(savedFilter.getWorkspaceId(), targetVisibility, requestedProjectId)
                : savedFilter.getProjectId();
        UUID targetTeamId = hasText(updateRequest.visibility()) || updateRequest.teamId() != null
                ? normalizeTeamId(savedFilter.getWorkspaceId(), targetVisibility, requestedTeamId)
                : savedFilter.getTeamId();
        requireSharedPermission(actorId, savedFilter.getWorkspaceId(), targetVisibility, targetProjectId);

        if (hasText(updateRequest.name())) {
            savedFilter.setName(updateRequest.name().trim());
        }
        savedFilter.setVisibility(targetVisibility);
        savedFilter.setProjectId(targetProjectId);
        savedFilter.setTeamId(targetTeamId);
        if (updateRequest.query() != null) {
            savedFilter.setQuery(validatedQuery(savedFilter.getWorkspaceId(), updateRequest.query()));
        }
        SavedFilter saved = savedFilterRepository.save(savedFilter);
        recordEvent(saved, "saved_filter.updated", actorId);
        return SavedFilterResponse.from(saved);
    }

    @Transactional
    public void delete(UUID savedFilterId) {
        UUID actorId = currentUserService.requireUserId();
        SavedFilter savedFilter = savedFilter(savedFilterId);
        activeWorkspace(savedFilter.getWorkspaceId());
        requireWritable(actorId, savedFilter);
        savedFilterRepository.delete(savedFilter);
        recordEvent(savedFilter, "saved_filter.deleted", actorId);
    }

    public SavedFilter requireReadableEntity(UUID savedFilterId, UUID actorId) {
        SavedFilter savedFilter = savedFilter(savedFilterId);
        activeWorkspace(savedFilter.getWorkspaceId());
        requireReadable(actorId, savedFilter);
        return savedFilter;
    }

    private JsonNode validatedQuery(UUID workspaceId, Object query) {
        JsonNode json = toJson(query);
        if (!json.isObject()) {
            throw badRequest("query must be a JSON object");
        }
        if (containsRawSqlKey(json)) {
            throw badRequest("saved filters must not contain raw SQL");
        }
        validateOptionalProjectScope(workspaceId, json, "projectId");
        JsonNode projectIds = json.path("projectIds");
        if (!projectIds.isMissingNode() && !projectIds.isNull()) {
            if (!projectIds.isArray()) {
                throw badRequest("query.projectIds must be an array");
            }
            projectIds.forEach(value -> validateProjectScope(workspaceId, parseUuid(value.asText(), "query.projectIds must contain only UUID values")));
        }
        validateOptionalTeamScope(workspaceId, json, "teamId");
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

    private void validateOptionalProjectScope(UUID workspaceId, JsonNode query, String fieldName) {
        JsonNode value = query.path(fieldName);
        if (!value.isMissingNode() && !value.isNull() && hasText(value.asText(null))) {
            validateProjectScope(workspaceId, parseUuid(value.asText(), "query." + fieldName + " must be a UUID"));
        }
    }

    private void validateProjectScope(UUID workspaceId, UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> badRequest("query project scope is not in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("query project scope is not in this workspace");
        }
    }

    private void validateOptionalTeamScope(UUID workspaceId, JsonNode query, String fieldName) {
        JsonNode value = query.path(fieldName);
        if (!value.isMissingNode() && !value.isNull() && hasText(value.asText(null))) {
            Team team = teamRepository.findByIdAndWorkspaceId(parseUuid(value.asText(), "query." + fieldName + " must be a UUID"), workspaceId)
                    .orElseThrow(() -> badRequest("query team scope is not in this workspace"));
            if (!"active".equals(team.getStatus())) {
                throw badRequest("query team scope is not active");
            }
        }
    }

    private SavedFilter savedFilter(UUID savedFilterId) {
        return savedFilterRepository.findById(savedFilterId).orElseThrow(() -> notFound("Saved filter not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> notFound("Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        activeWorkspace(project.getWorkspaceId());
        return project;
    }

    private Team activeTeam(UUID teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> notFound("Team not found"));
        if (!"active".equals(team.getStatus())) {
            throw notFound("Team not found");
        }
        activeWorkspace(team.getWorkspaceId());
        return team;
    }

    private UUID normalizeProjectId(UUID workspaceId, String visibility, UUID projectId) {
        if (!"project".equals(visibility)) {
            if (projectId != null) {
                throw badRequest("projectId is only valid for project saved filters");
            }
            return null;
        }
        if (projectId == null) {
            throw badRequest("projectId is required for project saved filters");
        }
        validateProjectScope(workspaceId, projectId);
        return projectId;
    }

    private UUID normalizeTeamId(UUID workspaceId, String visibility, UUID teamId) {
        if (!"team".equals(visibility)) {
            if (teamId != null) {
                throw badRequest("teamId is only valid for team saved filters");
            }
            return null;
        }
        if (teamId == null) {
            throw badRequest("teamId is required for team saved filters");
        }
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, workspaceId)
                .orElseThrow(() -> badRequest("Team not found in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw badRequest("Team is not active");
        }
        return team.getId();
    }

    private void requireReadable(UUID actorId, SavedFilter savedFilter) {
        if (!canRead(actorId, savedFilter, canManageReports(actorId, savedFilter.getWorkspaceId()))) {
            throw notFound("Saved filter not found");
        }
    }

    private boolean canRead(UUID actorId, SavedFilter savedFilter, boolean canManageWorkspaceReports) {
        if (canManageWorkspaceReports) {
            return true;
        }
        if (actorId.equals(savedFilter.getOwnerId())) {
            if ("project".equals(savedFilter.getVisibility()) && savedFilter.getProjectId() != null) {
                return canUseProject(actorId, savedFilter.getProjectId(), "report.read")
                        || canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read");
            }
            return canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read");
        }
        if ("workspace".equals(savedFilter.getVisibility()) || "public".equals(savedFilter.getVisibility())) {
            return canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read");
        }
        if ("project".equals(savedFilter.getVisibility()) && savedFilter.getProjectId() != null) {
            return canUseProject(actorId, savedFilter.getProjectId(), "report.read");
        }
        return "team".equals(savedFilter.getVisibility())
                && savedFilter.getTeamId() != null
                && canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read")
                && teamMembershipRepository.existsByTeamIdAndUserIdAndLeftAtIsNull(savedFilter.getTeamId(), actorId);
    }

    private void requireWritable(UUID actorId, SavedFilter savedFilter) {
        if ("private".equals(savedFilter.getVisibility())
                && actorId.equals(savedFilter.getOwnerId())
                && canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read")) {
            return;
        }
        if ("project".equals(savedFilter.getVisibility())
                && savedFilter.getProjectId() != null
                && canUseProject(actorId, savedFilter.getProjectId(), "report.manage")) {
            return;
        }
        if (canManageReports(actorId, savedFilter.getWorkspaceId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to manage this saved filter");
    }

    private void requireSharedPermission(UUID actorId, UUID workspaceId, String visibility, UUID projectId) {
        if ("private".equals(visibility)) {
            permissionService.requireWorkspacePermission(actorId, workspaceId, "report.read");
            return;
        }
        if ("project".equals(visibility)) {
            permissionService.requireProjectPermission(actorId, required(projectId, "projectId"), "report.manage");
            return;
        }
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.manage");
    }

    private boolean canManageReports(UUID actorId, UUID workspaceId) {
        return permissionService.canUseWorkspace(actorId, workspaceId, "report.manage");
    }

    private boolean canUseWorkspace(UUID actorId, UUID workspaceId, String permissionKey) {
        return permissionService.canUseWorkspace(actorId, workspaceId, permissionKey);
    }

    private boolean canUseProject(UUID actorId, UUID projectId, String permissionKey) {
        return permissionService.canUseProject(actorId, projectId, permissionKey);
    }

    private void recordEvent(SavedFilter savedFilter, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("savedFilterId", savedFilter.getId().toString())
                .put("savedFilterName", savedFilter.getName())
                .put("visibility", savedFilter.getVisibility())
                .put("actorUserId", actorId.toString());
        if (savedFilter.getProjectId() != null) {
            payload.put("projectId", savedFilter.getProjectId().toString());
        }
        if (savedFilter.getTeamId() != null) {
            payload.put("teamId", savedFilter.getTeamId().toString());
        }
        domainEventService.record(savedFilter.getWorkspaceId(), "saved_filter", savedFilter.getId(), eventType, payload);
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private String normalizeVisibility(String visibility) {
        String normalized = hasText(visibility) ? visibility.trim().toLowerCase() : "private";
        if (!List.of("private", "team", "project", "workspace", "public").contains(normalized)) {
            throw badRequest("visibility must be private, team, project, workspace, or public");
        }
        return normalized;
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw badRequest(message);
        }
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
