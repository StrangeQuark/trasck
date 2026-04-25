package com.strangequark.trasck.workspace;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.access.SystemAdminRepository;
import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.organization.Organization;
import com.strangequark.trasck.organization.OrganizationManagementService;
import com.strangequark.trasck.setup.WorkspaceSeedService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceManagementService {

    private static final Pattern NON_KEY = Pattern.compile("[^A-Za-z0-9]");

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final SystemAdminRepository systemAdminRepository;
    private final OrganizationManagementService organizationManagementService;
    private final WorkspaceSeedService workspaceSeedService;

    public WorkspaceManagementService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            SystemAdminRepository systemAdminRepository,
            OrganizationManagementService organizationManagementService,
            WorkspaceSeedService workspaceSeedService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.systemAdminRepository = systemAdminRepository;
        this.organizationManagementService = organizationManagementService;
        this.workspaceSeedService = workspaceSeedService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listOrganizationWorkspaces(UUID organizationId) {
        UUID actorId = currentUserService.requireUserId();
        if (!organizationManagementService.canReadOrganization(organizationId, actorId)) {
            throw forbidden();
        }
        if (organizationManagementService.canManageOrganization(organizationId, actorId)) {
            return workspaceRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAscKeyAsc(organizationId).stream()
                    .filter(this::availableWorkspace)
                    .map(WorkspaceResponse::from)
                    .toList();
        }

        Set<UUID> memberWorkspaceIds = workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(actorId, "active").stream()
                .map(WorkspaceMembership::getWorkspaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (memberWorkspaceIds.isEmpty()) {
            return List.of();
        }
        return workspaceRepository
                .findByOrganizationIdAndIdInAndDeletedAtIsNullOrderByNameAscKeyAsc(organizationId, memberWorkspaceIds)
                .stream()
                .filter(this::availableWorkspace)
                .map(WorkspaceResponse::from)
                .toList();
    }

    @Transactional
    public WorkspaceResponse createWorkspace(UUID organizationId, WorkspaceRequest request) {
        WorkspaceRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Organization organization = organizationManagementService.requireManageOrganization(organizationId, actorId);
        String name = requiredText(createRequest.name(), "name");
        String key = key(createRequest.key(), "key");
        if (workspaceRepository.existsByOrganizationIdAndKeyIgnoreCase(organization.getId(), key)) {
            throw conflict("A workspace with this key already exists in the organization");
        }

        Workspace workspace = new Workspace();
        workspace.setOrganizationId(organization.getId());
        workspace.setName(name);
        workspace.setKey(key);
        workspace.setTimezone(optionalText(createRequest.timezone(), "UTC"));
        workspace.setLocale(optionalText(createRequest.locale(), "en-US"));
        workspace.setStatus(normalizeStatus(createRequest.status(), "active"));
        workspace.setAnonymousReadEnabled(Boolean.TRUE.equals(createRequest.anonymousReadEnabled()));
        Workspace saved = workspaceRepository.save(workspace);
        workspaceSeedService.seedWorkspaceDefaults(saved, actorId);
        return WorkspaceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = workspace(workspaceId);
        requireReadWorkspace(workspace, actorId);
        return WorkspaceResponse.from(workspace);
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, WorkspaceRequest request) {
        WorkspaceRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = workspace(workspaceId);
        requireManageWorkspace(workspace, actorId);

        if (hasText(updateRequest.name())) {
            workspace.setName(updateRequest.name().trim());
        }
        if (hasText(updateRequest.key())) {
            String key = key(updateRequest.key(), "key");
            if (!key.equalsIgnoreCase(workspace.getKey())
                    && workspaceRepository.existsByOrganizationIdAndKeyIgnoreCase(workspace.getOrganizationId(), key)) {
                throw conflict("A workspace with this key already exists in the organization");
            }
            workspace.setKey(key);
        }
        if (updateRequest.timezone() != null) {
            workspace.setTimezone(optionalText(updateRequest.timezone(), "UTC"));
        }
        if (updateRequest.locale() != null) {
            workspace.setLocale(optionalText(updateRequest.locale(), "en-US"));
        }
        if (updateRequest.anonymousReadEnabled() != null) {
            workspace.setAnonymousReadEnabled(Boolean.TRUE.equals(updateRequest.anonymousReadEnabled()));
        }
        if (updateRequest.status() != null) {
            workspace.setStatus(normalizeStatus(updateRequest.status(), "active"));
        }
        workspace.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return WorkspaceResponse.from(workspaceRepository.save(workspace));
    }

    @Transactional
    public void archiveWorkspace(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = workspace(workspaceId);
        requireManageWorkspace(workspace, actorId);
        workspace.setStatus("archived");
        workspace.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        workspace.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        workspaceRepository.save(workspace);
    }

    @Transactional(readOnly = true)
    public Workspace requireActiveWorkspace(UUID workspaceId) {
        Workspace workspace = workspace(workspaceId);
        if (!activeWorkspace(workspace)) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    @Transactional(readOnly = true)
    public void requireReadWorkspace(Workspace workspace, UUID actorId) {
        if (!canReadWorkspace(workspace, actorId)) {
            throw forbidden();
        }
    }

    @Transactional(readOnly = true)
    public void requireManageWorkspace(Workspace workspace, UUID actorId) {
        if (!canManageWorkspace(workspace, actorId)) {
            throw forbidden();
        }
    }

    private boolean canReadWorkspace(Workspace workspace, UUID actorId) {
        return canManageWorkspace(workspace, actorId)
                || permissionService.canUseWorkspace(actorId, workspace.getId(), "workspace.read");
    }

    private boolean canManageWorkspace(Workspace workspace, UUID actorId) {
        return isSystemAdmin(actorId)
                || organizationManagementService.canManageOrganization(workspace.getOrganizationId(), actorId)
                || permissionService.canUseWorkspace(actorId, workspace.getId(), "workspace.admin");
    }

    private Workspace workspace(UUID workspaceId) {
        UUID id = required(workspaceId, "workspaceId");
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound("Workspace not found"));
        if (!availableWorkspace(workspace)) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private boolean availableWorkspace(Workspace workspace) {
        return workspace.getDeletedAt() == null && !"archived".equalsIgnoreCase(workspace.getStatus());
    }

    private boolean activeWorkspace(Workspace workspace) {
        return workspace.getDeletedAt() == null && "active".equalsIgnoreCase(workspace.getStatus());
    }

    private boolean isSystemAdmin(UUID actorId) {
        return systemAdminRepository.existsByUserIdAndActiveTrue(actorId);
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

    private String optionalText(String value, String defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        return value.trim();
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

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }
}
