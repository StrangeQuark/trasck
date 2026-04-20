package com.strangequark.trasck.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReleaseRoadmapService {

    private final ObjectMapper objectMapper;
    private final ReleaseRepository releaseRepository;
    private final ReleaseWorkItemRepository releaseWorkItemRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapItemRepository roadmapItemRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public ReleaseRoadmapService(
            ObjectMapper objectMapper,
            ReleaseRepository releaseRepository,
            ReleaseWorkItemRepository releaseWorkItemRepository,
            RoadmapRepository roadmapRepository,
            RoadmapItemRepository roadmapItemRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkItemRepository workItemRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.releaseRepository = releaseRepository;
        this.releaseWorkItemRepository = releaseWorkItemRepository;
        this.roadmapRepository = roadmapRepository;
        this.roadmapItemRepository = roadmapItemRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<ReleaseResponse> listReleases(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.read");
        return releaseRepository.findByProjectIdOrderByReleaseDateAscNameAsc(project.getId()).stream()
                .map(ReleaseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReleaseResponse getRelease(UUID releaseId) {
        UUID actorId = currentUserService.requireUserId();
        Release release = release(releaseId);
        permissionService.requireProjectPermission(actorId, release.getProjectId(), "project.read");
        return ReleaseResponse.from(release);
    }

    @Transactional
    public ReleaseResponse createRelease(UUID projectId, ReleaseRequest request) {
        ReleaseRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        validateReleaseDates(createRequest);
        Release release = new Release();
        release.setProjectId(project.getId());
        applyReleaseRequest(release, createRequest, true);
        Release saved = releaseRepository.save(release);
        recordReleaseEvent(project, saved, "release.created", actorId);
        return ReleaseResponse.from(saved);
    }

    @Transactional
    public ReleaseResponse updateRelease(UUID releaseId, ReleaseRequest request) {
        ReleaseRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Release release = release(releaseId);
        Project project = activeProject(release.getProjectId());
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        validateReleaseDates(updateRequest.startDate() == null ? release.getStartDate() : updateRequest.startDate(),
                updateRequest.releaseDate() == null ? release.getReleaseDate() : updateRequest.releaseDate());
        applyReleaseRequest(release, updateRequest, false);
        Release saved = releaseRepository.save(release);
        recordReleaseEvent(project, saved, "release.updated", actorId);
        return ReleaseResponse.from(saved);
    }

    @Transactional
    public void deleteRelease(UUID releaseId) {
        UUID actorId = currentUserService.requireUserId();
        Release release = release(releaseId);
        Project project = activeProject(release.getProjectId());
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        releaseRepository.delete(release);
        recordReleaseEvent(project, release, "release.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<ReleaseWorkItemResponse> listReleaseWorkItems(UUID releaseId) {
        UUID actorId = currentUserService.requireUserId();
        Release release = release(releaseId);
        permissionService.requireProjectPermission(actorId, release.getProjectId(), "project.read");
        return releaseWorkItemRepository.findByIdReleaseIdOrderByAddedAtAsc(release.getId()).stream()
                .map(ReleaseWorkItemResponse::from)
                .toList();
    }

    @Transactional
    public ReleaseWorkItemResponse addReleaseWorkItem(UUID releaseId, ReleaseWorkItemRequest request) {
        ReleaseWorkItemRequest addRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Release release = release(releaseId);
        Project project = activeProject(release.getProjectId());
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        WorkItem item = activeProjectWorkItem(project, required(addRequest.workItemId(), "workItemId"));
        ReleaseWorkItem releaseWorkItem = releaseWorkItemRepository.findByIdReleaseIdAndIdWorkItemId(release.getId(), item.getId())
                .orElseGet(ReleaseWorkItem::new);
        releaseWorkItem.setId(new ReleaseWorkItemId(release.getId(), item.getId()));
        releaseWorkItem.setAddedById(actorId);
        releaseWorkItem.setAddedAt(OffsetDateTime.now());
        ReleaseWorkItem saved = releaseWorkItemRepository.save(releaseWorkItem);
        recordReleaseWorkItemEvent(project, release, item, "release.work_item_added", actorId);
        return ReleaseWorkItemResponse.from(saved);
    }

    @Transactional
    public void removeReleaseWorkItem(UUID releaseId, UUID workItemId) {
        UUID actorId = currentUserService.requireUserId();
        Release release = release(releaseId);
        Project project = activeProject(release.getProjectId());
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        ReleaseWorkItem releaseWorkItem = releaseWorkItemRepository.findByIdReleaseIdAndIdWorkItemId(release.getId(), workItemId)
                .orElseThrow(() -> notFound("Release work item not found"));
        releaseWorkItemRepository.delete(releaseWorkItem);
        workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .ifPresent(item -> recordReleaseWorkItemEvent(project, release, item, "release.work_item_removed", actorId));
    }

    @Transactional(readOnly = true)
    public List<RoadmapResponse> listWorkspaceRoadmaps(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return roadmapRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .filter(roadmap -> canReadRoadmap(actorId, roadmap))
                .map(this::roadmapResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoadmapResponse> listProjectRoadmaps(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.read");
        return roadmapRepository.findByProjectIdOrderByNameAsc(project.getId()).stream()
                .filter(roadmap -> canReadRoadmap(actorId, roadmap))
                .map(this::roadmapResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmap(UUID roadmapId) {
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        activeWorkspace(roadmap.getWorkspaceId());
        if (!canReadRoadmap(actorId, roadmap)) {
            throw forbidden();
        }
        return roadmapResponse(roadmap);
    }

    @Transactional
    public RoadmapResponse createRoadmap(UUID workspaceId, RoadmapRequest request) {
        RoadmapRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        Project project = createRequest.projectId() == null ? null : validateProject(workspaceId, createRequest.projectId());
        requireRoadmapWrite(actorId, workspaceId, project);
        Roadmap roadmap = new Roadmap();
        roadmap.setWorkspaceId(workspaceId);
        roadmap.setProjectId(project == null ? null : project.getId());
        roadmap.setOwnerId(actorId);
        applyRoadmapRequest(workspaceId, roadmap, createRequest, true);
        Roadmap saved = roadmapRepository.save(roadmap);
        recordRoadmapEvent(saved, "roadmap.created", actorId);
        return roadmapResponse(saved);
    }

    @Transactional
    public RoadmapResponse updateRoadmap(UUID roadmapId, RoadmapRequest request) {
        RoadmapRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        activeWorkspace(roadmap.getWorkspaceId());
        Project targetProject = updateRequest.projectId() != null
                ? validateProject(roadmap.getWorkspaceId(), updateRequest.projectId())
                : roadmap.getProjectId() == null ? null : activeProject(roadmap.getProjectId());
        requireRoadmapWrite(actorId, roadmap.getWorkspaceId(), targetProject);
        if (updateRequest.projectId() != null) {
            roadmap.setProjectId(targetProject.getId());
        }
        applyRoadmapRequest(roadmap.getWorkspaceId(), roadmap, updateRequest, false);
        Roadmap saved = roadmapRepository.save(roadmap);
        recordRoadmapEvent(saved, "roadmap.updated", actorId);
        return roadmapResponse(saved);
    }

    @Transactional
    public void deleteRoadmap(UUID roadmapId) {
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        activeWorkspace(roadmap.getWorkspaceId());
        Project project = roadmap.getProjectId() == null ? null : activeProject(roadmap.getProjectId());
        requireRoadmapWrite(actorId, roadmap.getWorkspaceId(), project);
        roadmapRepository.delete(roadmap);
        recordRoadmapEvent(roadmap, "roadmap.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<RoadmapItemResponse> listRoadmapItems(UUID roadmapId) {
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        if (!canReadRoadmap(actorId, roadmap)) {
            throw forbidden();
        }
        return roadmapItemRepository.findByRoadmapIdOrderByPositionAsc(roadmap.getId()).stream()
                .map(RoadmapItemResponse::from)
                .toList();
    }

    @Transactional
    public RoadmapItemResponse createRoadmapItem(UUID roadmapId, RoadmapItemRequest request) {
        RoadmapItemRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        Project project = roadmap.getProjectId() == null ? null : activeProject(roadmap.getProjectId());
        requireRoadmapWrite(actorId, roadmap.getWorkspaceId(), project);
        WorkItem workItem = activeRoadmapWorkItem(roadmap, required(createRequest.workItemId(), "workItemId"));
        validateRoadmapDates(createRequest.startDate(), createRequest.endDate());
        RoadmapItem item = new RoadmapItem();
        item.setRoadmapId(roadmap.getId());
        item.setWorkItemId(workItem.getId());
        applyRoadmapItemRequest(item, createRequest, true);
        RoadmapItem saved = roadmapItemRepository.save(item);
        recordRoadmapItemEvent(roadmap, workItem, "roadmap.item_created", actorId);
        return RoadmapItemResponse.from(saved);
    }

    @Transactional
    public RoadmapItemResponse updateRoadmapItem(UUID roadmapId, UUID roadmapItemId, RoadmapItemRequest request) {
        RoadmapItemRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        Project project = roadmap.getProjectId() == null ? null : activeProject(roadmap.getProjectId());
        requireRoadmapWrite(actorId, roadmap.getWorkspaceId(), project);
        RoadmapItem item = roadmapItemRepository.findByIdAndRoadmapId(roadmapItemId, roadmap.getId())
                .orElseThrow(() -> notFound("Roadmap item not found"));
        WorkItem workItem = updateRequest.workItemId() == null
                ? activeRoadmapWorkItem(roadmap, item.getWorkItemId())
                : activeRoadmapWorkItem(roadmap, updateRequest.workItemId());
        validateRoadmapDates(updateRequest.startDate() == null ? item.getStartDate() : updateRequest.startDate(),
                updateRequest.endDate() == null ? item.getEndDate() : updateRequest.endDate());
        if (updateRequest.workItemId() != null) {
            item.setWorkItemId(workItem.getId());
        }
        applyRoadmapItemRequest(item, updateRequest, false);
        RoadmapItem saved = roadmapItemRepository.save(item);
        recordRoadmapItemEvent(roadmap, workItem, "roadmap.item_updated", actorId);
        return RoadmapItemResponse.from(saved);
    }

    @Transactional
    public void deleteRoadmapItem(UUID roadmapId, UUID roadmapItemId) {
        UUID actorId = currentUserService.requireUserId();
        Roadmap roadmap = roadmap(roadmapId);
        Project project = roadmap.getProjectId() == null ? null : activeProject(roadmap.getProjectId());
        requireRoadmapWrite(actorId, roadmap.getWorkspaceId(), project);
        RoadmapItem item = roadmapItemRepository.findByIdAndRoadmapId(roadmapItemId, roadmap.getId())
                .orElseThrow(() -> notFound("Roadmap item not found"));
        roadmapItemRepository.delete(item);
        workItemRepository.findByIdAndDeletedAtIsNull(item.getWorkItemId())
                .ifPresent(workItem -> recordRoadmapItemEvent(roadmap, workItem, "roadmap.item_deleted", actorId));
    }

    private void applyReleaseRequest(Release release, ReleaseRequest request, boolean create) {
        if (create || hasText(request.name())) {
            release.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.version())) {
            release.setVersion(requiredText(request.version(), "version"));
        }
        if (request.startDate() != null) {
            release.setStartDate(request.startDate());
        }
        if (request.releaseDate() != null) {
            release.setReleaseDate(request.releaseDate());
        }
        if (create || hasText(request.status())) {
            release.setStatus(hasText(request.status()) ? request.status().trim().toLowerCase() : "planned");
        }
        if (request.description() != null) {
            release.setDescription(request.description());
        }
    }

    private void applyRoadmapRequest(UUID workspaceId, Roadmap roadmap, RoadmapRequest request, boolean create) {
        if (create || hasText(request.name())) {
            roadmap.setName(requiredText(request.name(), "name"));
        }
        if (create || request.config() != null) {
            roadmap.setConfig(toJsonObject(request.config()));
        }
        if (create || hasText(request.visibility())) {
            roadmap.setVisibility(normalizeVisibility(request.visibility()));
        }
        if (roadmap.getProjectId() != null) {
            validateProject(workspaceId, roadmap.getProjectId());
        }
    }

    private void applyRoadmapItemRequest(RoadmapItem item, RoadmapItemRequest request, boolean create) {
        if (request.startDate() != null) {
            item.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            item.setEndDate(request.endDate());
        }
        if (request.position() != null) {
            item.setPosition(nonNegative(request.position(), "position"));
        } else if (create) {
            item.setPosition(0);
        }
        if (create || request.displayConfig() != null) {
            item.setDisplayConfig(toJsonObject(request.displayConfig()));
        }
    }

    private RoadmapResponse roadmapResponse(Roadmap roadmap) {
        return RoadmapResponse.from(roadmap, roadmapItemRepository.findByRoadmapIdOrderByPositionAsc(roadmap.getId()));
    }

    private boolean canReadRoadmap(UUID actorId, Roadmap roadmap) {
        if ("private".equals(roadmap.getVisibility()) && !actorId.equals(roadmap.getOwnerId())) {
            Project project = roadmap.getProjectId() == null ? null : activeProject(roadmap.getProjectId());
            if (project != null) {
                return permissionService.canUseProject(actorId, project.getId(), "board.admin");
            }
            return permissionService.canUseWorkspace(actorId, roadmap.getWorkspaceId(), "workspace.admin");
        }
        if (roadmap.getProjectId() != null) {
            return permissionService.canUseProject(actorId, roadmap.getProjectId(), "project.read");
        }
        return permissionService.canUseWorkspace(actorId, roadmap.getWorkspaceId(), "workspace.read");
    }

    private void requireRoadmapWrite(UUID actorId, UUID workspaceId, Project project) {
        if (project != null) {
            permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        } else {
            permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        }
    }

    private WorkItem activeProjectWorkItem(Project project, UUID workItemId) {
        WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(workItemId).orElseThrow(() -> notFound("Work item not found"));
        if (!project.getWorkspaceId().equals(item.getWorkspaceId()) || !project.getId().equals(item.getProjectId())) {
            throw badRequest("Work item must belong to this project");
        }
        return item;
    }

    private WorkItem activeRoadmapWorkItem(Roadmap roadmap, UUID workItemId) {
        WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(workItemId).orElseThrow(() -> notFound("Work item not found"));
        if (!roadmap.getWorkspaceId().equals(item.getWorkspaceId())) {
            throw badRequest("Work item must belong to this workspace");
        }
        if (roadmap.getProjectId() != null && !roadmap.getProjectId().equals(item.getProjectId())) {
            throw badRequest("Work item must belong to the roadmap project");
        }
        return item;
    }

    private Release release(UUID releaseId) {
        return releaseRepository.findById(releaseId).orElseThrow(() -> notFound("Release not found"));
    }

    private Roadmap roadmap(UUID roadmapId) {
        return roadmapRepository.findById(roadmapId).orElseThrow(() -> notFound("Roadmap not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> notFound("Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        return project;
    }

    private Project validateProject(UUID workspaceId, UUID projectId) {
        Project project = activeProject(projectId);
        if (!workspaceId.equals(project.getWorkspaceId())) {
            throw badRequest("Project not found in this workspace");
        }
        return project;
    }

    private void validateReleaseDates(ReleaseRequest request) {
        validateReleaseDates(request.startDate(), request.releaseDate());
    }

    private void validateReleaseDates(java.time.LocalDate startDate, java.time.LocalDate releaseDate) {
        if (startDate != null && releaseDate != null && startDate.isAfter(releaseDate)) {
            throw badRequest("startDate must be on or before releaseDate");
        }
    }

    private void validateRoadmapDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw badRequest("startDate must be on or before endDate");
        }
    }

    private String normalizeVisibility(String visibility) {
        String normalized = hasText(visibility) ? visibility.trim().toLowerCase() : "workspace";
        if (!List.of("private", "workspace", "public").contains(normalized)) {
            throw badRequest("visibility must be private, workspace, or public");
        }
        return normalized;
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

    private void recordReleaseEvent(Project project, Release release, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("releaseId", release.getId().toString())
                .put("releaseName", release.getName())
                .put("projectId", project.getId().toString())
                .put("actorUserId", actorId.toString());
        domainEventService.record(project.getWorkspaceId(), "release", release.getId(), eventType, payload);
    }

    private void recordReleaseWorkItemEvent(Project project, Release release, WorkItem item, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("releaseId", release.getId().toString())
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("projectId", project.getId().toString())
                .put("actorUserId", actorId.toString());
        domainEventService.record(project.getWorkspaceId(), "release", release.getId(), eventType, payload);
    }

    private void recordRoadmapEvent(Roadmap roadmap, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("roadmapId", roadmap.getId().toString())
                .put("roadmapName", roadmap.getName())
                .put("actorUserId", actorId.toString());
        if (roadmap.getProjectId() != null) {
            payload.put("projectId", roadmap.getProjectId().toString());
        }
        domainEventService.record(roadmap.getWorkspaceId(), "roadmap", roadmap.getId(), eventType, payload);
    }

    private void recordRoadmapItemEvent(Roadmap roadmap, WorkItem item, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("roadmapId", roadmap.getId().toString())
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("actorUserId", actorId.toString());
        domainEventService.record(roadmap.getWorkspaceId(), "roadmap", roadmap.getId(), eventType, payload);
    }

    private int nonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw badRequest(fieldName + " must be zero or greater");
        }
        return value;
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

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
