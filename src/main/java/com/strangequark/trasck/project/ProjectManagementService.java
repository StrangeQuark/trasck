package com.strangequark.trasck.project;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.setup.InitialSetupResponse;
import com.strangequark.trasck.setup.WorkspaceSeedService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceManagementService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectManagementService {

    private static final Pattern NON_KEY = Pattern.compile("[^A-Za-z0-9]");

    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final WorkspaceManagementService workspaceManagementService;
    private final WorkspaceSeedService workspaceSeedService;

    public ProjectManagementService(
            ProjectRepository projectRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            WorkspaceManagementService workspaceManagementService,
            WorkspaceSeedService workspaceSeedService
    ) {
        this.projectRepository = projectRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.workspaceManagementService = workspaceManagementService;
        this.workspaceSeedService = workspaceSeedService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listWorkspaceProjects(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = workspaceManagementService.requireActiveWorkspace(workspaceId);
        workspaceManagementService.requireReadWorkspace(workspace, actorId);
        return projectRepository.findByWorkspaceIdAndDeletedAtIsNullOrderByKeyAscNameAsc(workspace.getId()).stream()
                .filter(this::availableProject)
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional
    public ProjectResponse createProject(UUID workspaceId, ProjectRequest request) {
        ProjectRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = workspaceManagementService.requireActiveWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspace.getId(), "project.create");

        String name = requiredText(createRequest.name(), "name");
        String key = key(createRequest.key(), "key");
        if (projectRepository.existsByWorkspaceIdAndKeyIgnoreCase(workspace.getId(), key)) {
            throw conflict("A project with this key already exists in the workspace");
        }

        Project project = new Project();
        project.setWorkspaceId(workspace.getId());
        project.setParentProjectId(validateParentProject(workspace.getId(), createRequest.parentProjectId()));
        project.setName(name);
        project.setKey(key);
        project.setDescription(createRequest.description());
        project.setVisibility(normalizeVisibility(createRequest.visibility()));
        project.setStatus(normalizeStatus(createRequest.status(), "active"));
        project.setLeadUserId(validateWorkspaceUser(workspace.getId(), firstNonNull(createRequest.leadUserId(), actorId), "leadUserId"));
        Project saved = projectRepository.save(project);
        InitialSetupResponse.SeedDataSummary seedData = workspaceSeedService.seedProjectDefaultsWithSummary(workspace, saved, actorId);
        return ProjectResponse.from(saved, seedData);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = project(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.read");
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, ProjectRequest request) {
        ProjectRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Project project = project(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.admin");

        if (hasText(updateRequest.name())) {
            project.setName(updateRequest.name().trim());
        }
        if (hasText(updateRequest.key())) {
            String key = key(updateRequest.key(), "key");
            if (!key.equalsIgnoreCase(project.getKey())
                    && projectRepository.existsByWorkspaceIdAndKeyIgnoreCase(project.getWorkspaceId(), key)) {
                throw conflict("A project with this key already exists in the workspace");
            }
            project.setKey(key);
        }
        if (updateRequest.description() != null) {
            project.setDescription(updateRequest.description());
        }
        if (updateRequest.visibility() != null) {
            project.setVisibility(normalizeVisibility(updateRequest.visibility()));
        }
        if (updateRequest.status() != null) {
            project.setStatus(normalizeStatus(updateRequest.status(), "active"));
        }
        if (updateRequest.parentProjectId() != null) {
            UUID parentProjectId = validateParentProject(project.getWorkspaceId(), updateRequest.parentProjectId());
            if (parentProjectId.equals(project.getId())) {
                throw badRequest("parentProjectId cannot reference the project being updated");
            }
            project.setParentProjectId(parentProjectId);
        }
        if (updateRequest.leadUserId() != null) {
            project.setLeadUserId(validateWorkspaceUser(project.getWorkspaceId(), updateRequest.leadUserId(), "leadUserId"));
        }
        project.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void archiveProject(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = project(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.admin");
        project.setStatus("archived");
        project.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        project.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        projectRepository.save(project);
    }

    private Project project(UUID projectId) {
        UUID id = required(projectId, "projectId");
        Project project = projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound("Project not found"));
        if (!availableProject(project)) {
            throw notFound("Project not found");
        }
        return project;
    }

    private boolean availableProject(Project project) {
        return project.getDeletedAt() == null && "active".equalsIgnoreCase(project.getStatus());
    }

    private UUID validateParentProject(UUID workspaceId, UUID parentProjectId) {
        if (parentProjectId == null) {
            return null;
        }
        Project parentProject = projectRepository.findByIdAndDeletedAtIsNull(parentProjectId)
                .orElseThrow(() -> badRequest("parentProjectId must reference an active project in the same workspace"));
        if (!workspaceId.equals(parentProject.getWorkspaceId()) || !availableProject(parentProject)) {
            throw badRequest("parentProjectId must reference an active project in the same workspace");
        }
        return parentProject.getId();
    }

    private UUID validateWorkspaceUser(UUID workspaceId, UUID userId, String fieldName) {
        if (userId == null) {
            return null;
        }
        if (!workspaceMembershipRepository.existsByWorkspaceIdAndUserIdAndStatusIgnoreCase(workspaceId, userId, "active")) {
            throw badRequest(fieldName + " must be an active workspace member");
        }
        return userId;
    }

    private String normalizeVisibility(String visibility) {
        String normalized = hasText(visibility) ? visibility.trim().toLowerCase() : "private";
        if (!List.of("private", "workspace", "public").contains(normalized)) {
            throw badRequest("visibility must be private, workspace, or public");
        }
        return normalized;
    }

    private String normalizeStatus(String status, String defaultStatus) {
        String normalized = hasText(status) ? status.trim().toLowerCase() : defaultStatus;
        if (!List.of("active", "inactive", "archived").contains(normalized)) {
            throw badRequest("status must be active, inactive, or archived");
        }
        return normalized;
    }

    private String key(String value, String fieldName) {
        String normalized = NON_KEY.matcher(requiredText(value, fieldName)).replaceAll("").toUpperCase();
        if (normalized.isBlank()) {
            throw badRequest(fieldName + " must include at least one letter or digit");
        }
        return normalized;
    }

    private UUID firstNonNull(UUID first, UUID second) {
        return first == null ? second : first;
    }

    private UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
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

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
