package com.strangequark.trasck.organization;

import com.strangequark.trasck.access.RolePermissionRepository;
import com.strangequark.trasck.access.SystemAdminRepository;
import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
public class OrganizationManagementService {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final OrganizationRepository organizationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final CurrentUserService currentUserService;

    public OrganizationManagementService(
            OrganizationRepository organizationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            RolePermissionRepository rolePermissionRepository,
            SystemAdminRepository systemAdminRepository,
            CurrentUserService currentUserService
    ) {
        this.organizationRepository = organizationRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.systemAdminRepository = systemAdminRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizations() {
        UUID actorId = currentUserService.requireUserId();
        if (isSystemAdmin(actorId)) {
            return organizationRepository.findByDeletedAtIsNullOrderByNameAscSlugAsc().stream()
                    .map(OrganizationResponse::from)
                    .toList();
        }

        Map<UUID, Organization> organizations = new LinkedHashMap<>();
        organizationRepository.findByCreatedByIdAndDeletedAtIsNullOrderByNameAscSlugAsc(actorId)
                .forEach(organization -> organizations.put(organization.getId(), organization));

        Set<UUID> workspaceIds = workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(actorId, "active").stream()
                .map(WorkspaceMembership::getWorkspaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!workspaceIds.isEmpty()) {
            Set<UUID> organizationIds = workspaceRepository.findByIdInAndDeletedAtIsNullOrderByNameAscKeyAsc(workspaceIds).stream()
                    .filter(this::activeWorkspace)
                    .map(Workspace::getOrganizationId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            organizationRepository.findAllById(organizationIds).stream()
                    .filter(this::availableOrganization)
                    .forEach(organization -> organizations.put(organization.getId(), organization));
        }

        return organizations.values().stream()
                .sorted(Comparator.comparing(Organization::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Organization::getSlug, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(OrganizationResponse::from)
                .toList();
    }

    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest request) {
        OrganizationRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        String name = requiredText(createRequest.name(), "name");
        String slug = slug(createRequest.slug(), "slug");
        if (organizationRepository.existsBySlugIgnoreCase(slug)) {
            throw conflict("An organization with this slug already exists");
        }

        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(slug);
        organization.setStatus(normalizeStatus(createRequest.status(), "active"));
        organization.setCreatedById(actorId);
        return OrganizationResponse.from(organizationRepository.save(organization));
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID organizationId) {
        UUID actorId = currentUserService.requireUserId();
        Organization organization = organization(organizationId);
        requireReadOrganization(organization, actorId);
        return OrganizationResponse.from(organization);
    }

    @Transactional
    public OrganizationResponse updateOrganization(UUID organizationId, OrganizationRequest request) {
        OrganizationRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Organization organization = organization(organizationId);
        requireManageOrganization(organization, actorId);

        if (hasText(updateRequest.name())) {
            organization.setName(updateRequest.name().trim());
        }
        if (hasText(updateRequest.slug())) {
            String slug = slug(updateRequest.slug(), "slug");
            if (!slug.equalsIgnoreCase(organization.getSlug()) && organizationRepository.existsBySlugIgnoreCase(slug)) {
                throw conflict("An organization with this slug already exists");
            }
            organization.setSlug(slug);
        }
        if (updateRequest.status() != null) {
            organization.setStatus(normalizeStatus(updateRequest.status(), "active"));
        }
        organization.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return OrganizationResponse.from(organizationRepository.save(organization));
    }

    @Transactional
    public void archiveOrganization(UUID organizationId) {
        UUID actorId = currentUserService.requireUserId();
        Organization organization = organization(organizationId);
        requireManageOrganization(organization, actorId);
        boolean hasActiveWorkspaces = workspaceRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAscKeyAsc(organization.getId()).stream()
                .anyMatch(this::activeWorkspace);
        if (hasActiveWorkspaces) {
            throw conflict("Archive all active workspaces before archiving this organization");
        }
        organization.setStatus("archived");
        organization.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        organization.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        organizationRepository.save(organization);
    }

    @Transactional(readOnly = true)
    public Organization requireManageOrganization(UUID organizationId, UUID actorId) {
        Organization organization = organization(organizationId);
        requireManageOrganization(organization, actorId);
        return organization;
    }

    @Transactional(readOnly = true)
    public boolean canManageOrganization(UUID organizationId, UUID actorId) {
        return canManageOrganization(organization(organizationId), actorId);
    }

    @Transactional(readOnly = true)
    public boolean canReadOrganization(UUID organizationId, UUID actorId) {
        Organization organization = organization(organizationId);
        return canReadOrganization(organization, actorId);
    }

    private void requireReadOrganization(Organization organization, UUID actorId) {
        if (!canReadOrganization(organization, actorId)) {
            throw forbidden();
        }
    }

    private void requireManageOrganization(Organization organization, UUID actorId) {
        if (!canManageOrganization(organization, actorId)) {
            throw forbidden();
        }
    }

    private boolean canReadOrganization(Organization organization, UUID actorId) {
        if (canManageOrganization(organization, actorId)) {
            return true;
        }
        Set<UUID> workspaceIds = workspaceRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAscKeyAsc(organization.getId()).stream()
                .filter(this::activeWorkspace)
                .map(Workspace::getId)
                .collect(Collectors.toSet());
        if (workspaceIds.isEmpty()) {
            return false;
        }
        return workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(actorId, "active").stream()
                .map(WorkspaceMembership::getWorkspaceId)
                .anyMatch(workspaceIds::contains);
    }

    private boolean canManageOrganization(Organization organization, UUID actorId) {
        if (isSystemAdmin(actorId) || Objects.equals(organization.getCreatedById(), actorId)) {
            return true;
        }
        Set<UUID> workspaceIds = workspaceRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAscKeyAsc(organization.getId()).stream()
                .filter(this::activeWorkspace)
                .map(Workspace::getId)
                .collect(Collectors.toSet());
        if (workspaceIds.isEmpty()) {
            return false;
        }
        return workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(actorId, "active").stream()
                .filter(membership -> workspaceIds.contains(membership.getWorkspaceId()))
                .anyMatch(membership -> rolePermissionRepository.roleHasPermission(membership.getRoleId(), "workspace.admin"));
    }

    private Organization organization(UUID organizationId) {
        UUID id = required(organizationId, "organizationId");
        Organization organization = organizationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound("Organization not found"));
        if (!availableOrganization(organization)) {
            throw notFound("Organization not found");
        }
        return organization;
    }

    private boolean availableOrganization(Organization organization) {
        return organization.getDeletedAt() == null && !"archived".equalsIgnoreCase(organization.getStatus());
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

    private String slug(String value, String fieldName) {
        String normalized = NON_SLUG.matcher(requiredText(value, fieldName).toLowerCase())
                .replaceAll("-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            throw badRequest(fieldName + " must include at least one letter or digit");
        }
        return normalized;
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
