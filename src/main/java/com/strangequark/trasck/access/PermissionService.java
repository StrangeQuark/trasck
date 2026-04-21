package com.strangequark.trasck.access;

import com.strangequark.trasck.identity.TrasckPrincipal;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service("permissionService")
public class PermissionService {

    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PermissionService(
            ProjectRepository projectRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            ProjectMembershipRepository projectMembershipRepository,
            RolePermissionRepository rolePermissionRepository
    ) {
        this.projectRepository = projectRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.projectMembershipRepository = projectMembershipRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Transactional(readOnly = true)
    public boolean canManageUsers(Authentication authentication, UUID workspaceId) {
        return principal(authentication)
                .filter(principal -> principal.allowsScope("user.manage"))
                .map(principal -> hasWorkspacePermission(principal.userId(), workspaceId, "user.manage"))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canManageWorkspace(Authentication authentication, UUID workspaceId) {
        return principal(authentication)
                .filter(principal -> principal.allowsScope("workspace.admin"))
                .map(principal -> hasWorkspacePermission(principal.userId(), workspaceId, "workspace.admin"))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canAccessOpenApiDocs(Authentication authentication) {
        return principal(authentication)
                .filter(principal -> principal.allowsScope("workspace.admin"))
                .map(principal -> workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(principal.userId(), "active").stream()
                        .anyMatch(membership -> rolePermissionRepository.roleHasPermission(membership.getRoleId(), "workspace.admin")))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canUseWorkspace(Authentication authentication, UUID workspaceId, String permissionKey) {
        return principal(authentication)
                .filter(principal -> principal.allowsScope(permissionKey))
                .map(principal -> hasWorkspacePermission(principal.userId(), workspaceId, permissionKey))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canUseProject(Authentication authentication, UUID projectId, String permissionKey) {
        return principal(authentication)
                .filter(principal -> principal.allowsScope(permissionKey))
                .map(principal -> hasProjectPermission(principal.userId(), projectId, permissionKey))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canUseWorkspace(UUID userId, UUID workspaceId, String permissionKey) {
        return currentPrincipalAllows(permissionKey) && hasWorkspacePermission(userId, workspaceId, permissionKey);
    }

    @Transactional(readOnly = true)
    public boolean canUseProject(UUID userId, UUID projectId, String permissionKey) {
        return currentPrincipalAllows(permissionKey) && hasProjectPermission(userId, projectId, permissionKey);
    }

    @Transactional(readOnly = true)
    public void requireWorkspacePermission(UUID userId, UUID workspaceId, String permissionKey) {
        if (!currentPrincipalAllows(permissionKey) || !hasWorkspacePermission(userId, workspaceId, permissionKey)) {
            throw forbidden();
        }
    }

    @Transactional(readOnly = true)
    public void requireProjectPermission(UUID userId, UUID projectId, String permissionKey) {
        if (!currentPrincipalAllows(permissionKey) || !hasProjectPermission(userId, projectId, permissionKey)) {
            throw forbidden();
        }
    }

    @Transactional(readOnly = true)
    public boolean hasProjectPermission(UUID userId, UUID projectId, String permissionKey) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getDeletedAt() != null || !"active".equals(project.getStatus())) {
            return false;
        }
        if (hasWorkspacePermission(userId, project.getWorkspaceId(), permissionKey)) {
            return true;
        }
        return projectMembershipRepository.findByProjectIdAndUserIdAndStatusIgnoreCase(projectId, userId, "active")
                .map(ProjectMembership::getRoleId)
                .filter(roleId -> rolePermissionRepository.roleHasPermission(roleId, permissionKey))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasWorkspacePermission(UUID userId, UUID workspaceId, String permissionKey) {
        return workspaceMembershipRepository.findByWorkspaceIdAndUserIdAndStatusIgnoreCase(workspaceId, userId, "active")
                .map(WorkspaceMembership::getRoleId)
                .filter(roleId -> rolePermissionRepository.roleHasPermission(roleId, permissionKey))
                .isPresent();
    }

    private Optional<TrasckPrincipal> principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TrasckPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    private boolean currentPrincipalAllows(String permissionKey) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return principal(authentication).map(principal -> principal.allowsScope(permissionKey)).orElse(true);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }
}
