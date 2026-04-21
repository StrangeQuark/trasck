package com.strangequark.trasck.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.identity.UserInvitationRepository;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoleManagementService {

    private static final String ACTIVE = "active";
    private static final String ARCHIVED = "archived";

    private final ObjectMapper objectMapper;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleVersionRepository roleVersionRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final UserInvitationRepository userInvitationRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final CurrentUserService currentUserService;
    private final DomainEventService domainEventService;

    public RoleManagementService(
            ObjectMapper objectMapper,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            RoleVersionRepository roleVersionRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            ProjectMembershipRepository projectMembershipRepository,
            UserInvitationRepository userInvitationRepository,
            ProjectRepository projectRepository,
            PermissionService permissionService,
            CurrentUserService currentUserService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleVersionRepository = roleVersionRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.projectMembershipRepository = projectMembershipRepository;
        this.userInvitationRepository = userInvitationRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.currentUserService = currentUserService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByCategoryAscKeyAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listWorkspaceRoles(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        return roleRepository.findByWorkspaceIdAndProjectIdIsNullAndStatusIgnoreCaseOrderByNameAsc(workspaceId, ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listProjectRoles(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        return roleRepository.findByProjectIdAndStatusIgnoreCaseOrderByNameAsc(projectId, ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getWorkspaceRole(UUID workspaceId, UUID roleId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        return toResponse(workspaceRole(workspaceId, roleId));
    }

    @Transactional(readOnly = true)
    public RoleResponse getProjectRole(UUID projectId, UUID roleId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        return toResponse(projectRole(projectId, roleId));
    }

    @Transactional
    public RoleResponse createWorkspaceRole(UUID workspaceId, RoleRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        String normalizedKey = normalizeKey(requiredText(request.key(), "key"));
        roleRepository.findByWorkspaceIdAndKeyIgnoreCaseAndProjectIdIsNull(workspaceId, normalizedKey)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Workspace role key already exists");
                });
        Role role = new Role();
        role.setWorkspaceId(workspaceId);
        role.setKey(normalizedKey);
        role.setName(requiredText(request.name(), "name"));
        role.setDescription(blankToNull(request.description()));
        role.setScope("workspace");
        role.setSystemRole(false);
        role.setStatus(ACTIVE);
        Role saved = roleRepository.save(role);
        replacePermissions(saved, normalizePermissionKeys(request.permissionKeys()));
        recordVersion(saved, permissionKeys(saved.getId()), "created", "Role created", actorId);
        recordRoleEvent(saved, "role.created", actorId);
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse createProjectRole(UUID projectId, RoleRequest request) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        String normalizedKey = normalizeKey(requiredText(request.key(), "key"));
        roleRepository.findByProjectIdAndKeyIgnoreCase(projectId, normalizedKey)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Project role key already exists");
                });
        Role role = new Role();
        role.setWorkspaceId(project.getWorkspaceId());
        role.setProjectId(projectId);
        role.setKey(normalizedKey);
        role.setName(requiredText(request.name(), "name"));
        role.setDescription(blankToNull(request.description()));
        role.setScope("project");
        role.setSystemRole(false);
        role.setStatus(ACTIVE);
        Role saved = roleRepository.save(role);
        replacePermissions(saved, normalizePermissionKeys(request.permissionKeys()));
        recordVersion(saved, permissionKeys(saved.getId()), "created", "Role created", actorId);
        recordRoleEvent(saved, "role.created", actorId);
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse updateWorkspaceRole(UUID workspaceId, UUID roleId, RoleUpdateRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        Role role = workspaceRole(workspaceId, roleId);
        ensureBaseline(role, actorId);
        role.setName(requiredText(request.name(), "name"));
        role.setDescription(blankToNull(request.description()));
        Role saved = roleRepository.save(role);
        recordVersion(saved, permissionKeys(saved.getId()), "metadata_updated", "Role metadata updated", actorId);
        recordRoleEvent(saved, "role.metadata_updated", actorId);
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse updateProjectRole(UUID projectId, UUID roleId, RoleUpdateRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        Role role = projectRole(projectId, roleId);
        ensureBaseline(role, actorId);
        role.setName(requiredText(request.name(), "name"));
        role.setDescription(blankToNull(request.description()));
        Role saved = roleRepository.save(role);
        recordVersion(saved, permissionKeys(saved.getId()), "metadata_updated", "Role metadata updated", actorId);
        recordRoleEvent(saved, "role.metadata_updated", actorId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RoleImpactPreviewResponse previewWorkspacePermissions(UUID workspaceId, UUID roleId, RolePermissionPreviewRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        return preview(workspaceRole(workspaceId, roleId), normalizePermissionKeys(request.permissionKeys()));
    }

    @Transactional(readOnly = true)
    public RoleImpactPreviewResponse previewProjectPermissions(UUID projectId, UUID roleId, RolePermissionPreviewRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        return preview(projectRole(projectId, roleId), normalizePermissionKeys(request.permissionKeys()));
    }

    @Transactional
    public RoleResponse updateWorkspacePermissions(UUID workspaceId, UUID roleId, RolePermissionUpdateRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        Role role = workspaceRole(workspaceId, roleId);
        Set<String> requested = normalizePermissionKeys(request.permissionKeys());
        applyConfirmedPermissionChange(role, requested, request, actorId);
        recordRoleEvent(role, "role.permissions_updated", actorId);
        return toResponse(role);
    }

    @Transactional
    public RoleResponse updateProjectPermissions(UUID projectId, UUID roleId, RolePermissionUpdateRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        Role role = projectRole(projectId, roleId);
        Set<String> requested = normalizePermissionKeys(request.permissionKeys());
        applyConfirmedPermissionChange(role, requested, request, actorId);
        recordRoleEvent(role, "role.permissions_updated", actorId);
        return toResponse(role);
    }

    @Transactional
    public void archiveWorkspaceRole(UUID workspaceId, UUID roleId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        Role role = workspaceRole(workspaceId, roleId);
        archiveRole(role, actorId);
    }

    @Transactional
    public void archiveProjectRole(UUID projectId, UUID roleId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        Role role = projectRole(projectId, roleId);
        archiveRole(role, actorId);
    }

    @Transactional(readOnly = true)
    public List<RoleVersionResponse> workspaceRoleVersions(UUID workspaceId, UUID roleId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        Role role = workspaceRole(workspaceId, roleId);
        return roleVersionRepository.findByRoleIdOrderByVersionNumberDesc(role.getId()).stream()
                .map(RoleVersionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleVersionResponse> projectRoleVersions(UUID projectId, UUID roleId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        Role role = projectRole(projectId, roleId);
        return roleVersionRepository.findByRoleIdOrderByVersionNumberDesc(role.getId()).stream()
                .map(RoleVersionResponse::from)
                .toList();
    }

    @Transactional
    public RoleResponse rollbackWorkspaceRole(UUID workspaceId, UUID roleId, UUID versionId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "user.manage");
        return rollback(workspaceRole(workspaceId, roleId), versionId, actorId);
    }

    @Transactional
    public RoleResponse rollbackProjectRole(UUID projectId, UUID roleId, UUID versionId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.admin");
        return rollback(projectRole(projectId, roleId), versionId, actorId);
    }

    private void applyConfirmedPermissionChange(Role role, Set<String> requested, RolePermissionUpdateRequest request, UUID actorId) {
        ensureBaseline(role, actorId);
        RoleImpactPreviewResponse preview = preview(role, requested);
        if (!Boolean.TRUE.equals(request.confirmed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role permission changes require preview confirmation");
        }
        if (!preview.previewToken().equals(request.previewToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role permission preview is stale");
        }
        assertAdministrativeAccessRemains(role, requested);
        replacePermissions(role, requested);
        recordVersion(role, permissionKeys(role.getId()), "permissions_updated", "Role permissions updated", actorId);
    }

    private RoleResponse rollback(Role role, UUID versionId, UUID actorId) {
        ensureBaseline(role, actorId);
        RoleVersion version = roleVersionRepository.findByIdAndRoleId(versionId, role.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role version not found"));
        Set<String> requestedPermissions = normalizePermissionKeys(toStringList(version.getPermissionKeys()));
        RoleImpactPreviewResponse preview = preview(role, requestedPermissions);
        assertAdministrativeAccessRemains(role, requestedPermissions);
        role.setName(version.getName());
        role.setDescription(version.getDescription());
        role.setStatus(version.getStatus());
        role.setArchivedAt(ARCHIVED.equals(version.getStatus()) ? OffsetDateTime.now() : null);
        Role saved = roleRepository.save(role);
        replacePermissions(saved, requestedPermissions);
        recordVersion(saved, permissionKeys(saved.getId()), "rollback", "Rolled back to version " + version.getVersionNumber(), actorId);
        recordRoleEvent(saved, "role.rollback", actorId);
        return RoleResponse.from(saved, permissionKeys(saved.getId()), preview.impactSummary());
    }

    private void archiveRole(Role role, UUID actorId) {
        RoleImpactSummary impact = impactSummary(role);
        if (impact.activeMembers() > 0 || impact.pendingInvitations() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role cannot be archived while it is assigned to active members or pending invitations");
        }
        ensureBaseline(role, actorId);
        role.setStatus(ARCHIVED);
        role.setArchivedAt(OffsetDateTime.now());
        Role saved = roleRepository.save(role);
        recordVersion(saved, permissionKeys(saved.getId()), "archived", "Role archived", actorId);
        recordRoleEvent(saved, "role.archived", actorId);
    }

    private RoleImpactPreviewResponse preview(Role role, Set<String> requested) {
        List<String> current = permissionKeys(role.getId());
        List<String> requestedList = sorted(requested);
        List<String> added = requestedList.stream()
                .filter(key -> !current.contains(key))
                .toList();
        List<String> removed = current.stream()
                .filter(key -> !requested.contains(key))
                .toList();
        boolean removesAdmin = ("workspace".equals(role.getScope()) && current.contains("workspace.admin") && !requested.contains("workspace.admin"))
                || ("project".equals(role.getScope()) && current.contains("project.admin") && !requested.contains("project.admin"));
        RoleImpactSummary impact = impactSummary(role);
        return new RoleImpactPreviewResponse(
                role.getId(),
                current,
                requestedList,
                added,
                removed,
                impact,
                removesAdmin,
                true,
                "Apply permission changes to " + impact.affectedUsers() + " active member(s) and " + impact.pendingInvitations() + " pending invitation(s)",
                previewToken(role, current, requestedList, impact)
        );
    }

    private void assertAdministrativeAccessRemains(Role role, Set<String> requested) {
        if ("workspace".equals(role.getScope()) && permissionKeys(role.getId()).contains("workspace.admin") && !requested.contains("workspace.admin")) {
            boolean hasRemainingAdmin = workspaceMembershipRepository.findByWorkspaceIdAndStatusIgnoreCaseOrderByJoinedAtDescCreatedAtDesc(role.getWorkspaceId(), ACTIVE).stream()
                    .anyMatch(membership -> permissionKeysAfter(role, requested, membership.getRoleId()).contains("workspace.admin"));
            if (!hasRemainingAdmin) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active workspace admin role assignment must remain");
            }
        }
        if ("project".equals(role.getScope()) && permissionKeys(role.getId()).contains("project.admin") && !requested.contains("project.admin")) {
            Project project = activeProject(role.getProjectId());
            boolean workspaceAdminPath = workspaceMembershipRepository.findByWorkspaceIdAndStatusIgnoreCaseOrderByJoinedAtDescCreatedAtDesc(project.getWorkspaceId(), ACTIVE).stream()
                    .anyMatch(membership -> permissionKeys(membership.getRoleId()).contains("project.admin"));
            boolean hasRemainingProjectAdmin = projectMembershipRepository.findByProjectIdAndStatusIgnoreCaseOrderByCreatedAtDesc(role.getProjectId(), ACTIVE).stream()
                    .anyMatch(membership -> permissionKeysAfter(role, requested, membership.getRoleId()).contains("project.admin"));
            if (!workspaceAdminPath && !hasRemainingProjectAdmin) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active project admin role assignment must remain");
            }
        }
    }

    private List<String> permissionKeysAfter(Role changedRole, Set<String> requested, UUID roleId) {
        if (changedRole.getId().equals(roleId)) {
            return sorted(requested);
        }
        return permissionKeys(roleId);
    }

    private RoleImpactSummary impactSummary(Role role) {
        UUID actorId = currentUserService.requireUserId();
        if ("workspace".equals(role.getScope())) {
            long activeMembers = workspaceMembershipRepository.countByWorkspaceIdAndRoleIdAndStatusIgnoreCase(role.getWorkspaceId(), role.getId(), ACTIVE);
            long pendingInvitations = userInvitationRepository.countByWorkspaceIdAndRoleIdAndStatusIgnoreCase(role.getWorkspaceId(), role.getId(), "pending");
            boolean affectsCurrentUser = workspaceMembershipRepository.findByWorkspaceIdAndUserIdAndStatusIgnoreCase(role.getWorkspaceId(), actorId, ACTIVE)
                    .map(WorkspaceMembership::getRoleId)
                    .filter(role.getId()::equals)
                    .isPresent();
            return new RoleImpactSummary(activeMembers, pendingInvitations, activeMembers, affectsCurrentUser);
        }
        long activeMembers = projectMembershipRepository.countByProjectIdAndRoleIdAndStatusIgnoreCase(role.getProjectId(), role.getId(), ACTIVE);
        long pendingInvitations = userInvitationRepository.countByProjectIdAndProjectRoleIdAndStatusIgnoreCase(role.getProjectId(), role.getId(), "pending");
        boolean affectsCurrentUser = projectMembershipRepository.findByProjectIdAndUserIdAndStatusIgnoreCase(role.getProjectId(), actorId, ACTIVE)
                .map(ProjectMembership::getRoleId)
                .filter(role.getId()::equals)
                .isPresent();
        return new RoleImpactSummary(activeMembers, pendingInvitations, activeMembers, affectsCurrentUser);
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.from(role, permissionKeys(role.getId()), impactSummary(role));
    }

    private Role workspaceRole(UUID workspaceId, UUID roleId) {
        return roleRepository.findByIdAndWorkspaceIdAndProjectIdIsNull(roleId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace role not found"));
    }

    private Role projectRole(UUID projectId, UUID roleId) {
        return roleRepository.findByIdAndProjectId(roleId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project role not found"));
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (project.getDeletedAt() != null || !"active".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }

    private void replacePermissions(Role role, Set<String> requestedKeys) {
        Map<String, Permission> permissions = permissionRepository.findByKeyIn(requestedKeys).stream()
                .collect(Collectors.toMap(Permission::getKey, Function.identity()));
        if (permissions.size() != requestedKeys.size()) {
            Set<String> unknown = new LinkedHashSet<>(requestedKeys);
            unknown.removeAll(permissions.keySet());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission keys: " + String.join(", ", unknown));
        }
        rolePermissionRepository.deleteByIdRoleId(role.getId());
        List<RolePermission> joins = requestedKeys.stream()
                .map(permissions::get)
                .filter(Objects::nonNull)
                .map(permission -> {
                    RolePermission join = new RolePermission();
                    join.setId(new RolePermissionId(role.getId(), permission.getId()));
                    return join;
                })
                .toList();
        rolePermissionRepository.saveAll(joins);
    }

    private void ensureBaseline(Role role, UUID actorId) {
        if (roleVersionRepository.countByRoleId(role.getId()) == 0) {
            recordVersion(role, permissionKeys(role.getId()), "baseline", "Initial role snapshot", actorId);
        }
    }

    private void recordVersion(Role role, List<String> permissionKeys, String changeType, String note, UUID actorId) {
        RoleVersion version = new RoleVersion();
        version.setRoleId(role.getId());
        version.setWorkspaceId(role.getWorkspaceId());
        version.setProjectId(role.getProjectId());
        version.setVersionNumber(roleVersionRepository.maxVersionNumber(role.getId()) + 1);
        version.setName(role.getName());
        version.setKey(role.getKey());
        version.setScope(role.getScope());
        version.setDescription(role.getDescription());
        version.setSystemRole(Boolean.TRUE.equals(role.getSystemRole()));
        version.setStatus(role.getStatus() == null ? ACTIVE : role.getStatus());
        version.setPermissionKeys(permissionArray(permissionKeys));
        version.setChangeType(changeType);
        version.setChangeNote(note);
        version.setCreatedById(actorId);
        roleVersionRepository.save(version);
    }

    private ArrayNode permissionArray(Collection<String> permissionKeys) {
        ArrayNode array = objectMapper.createArrayNode();
        sorted(new HashSet<>(permissionKeys)).forEach(array::add);
        return array;
    }

    private List<String> permissionKeys(UUID roleId) {
        return rolePermissionRepository.findPermissionKeysByRoleId(roleId);
    }

    private Set<String> normalizePermissionKeys(Collection<String> permissionKeys) {
        if (permissionKeys == null) {
            return Set.of();
        }
        return permissionKeys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> toStringList(com.fasterxml.jackson.databind.JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private List<String> sorted(Set<String> keys) {
        return keys.stream().sorted().toList();
    }

    private String previewToken(Role role, List<String> currentPermissionKeys, List<String> requestedPermissionKeys, RoleImpactSummary impact) {
        String material = role.getId()
                + "|" + String.join(",", currentPermissionKeys)
                + "|" + String.join(",", requestedPermissionKeys)
                + "|" + role.getUpdatedAt()
                + "|" + impact.activeMembers()
                + "|" + impact.pendingInvitations();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte part : hashed) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private void recordRoleEvent(Role role, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("roleId", role.getId().toString())
                .put("roleKey", role.getKey())
                .put("scope", role.getScope())
                .put("actorId", actorId.toString());
        if (role.getProjectId() != null) {
            payload.put("projectId", role.getProjectId().toString());
        }
        domainEventService.record(role.getWorkspaceId(), "role", role.getId(), eventType, payload);
    }

    private String normalizeKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key is required");
        }
        return normalized;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
