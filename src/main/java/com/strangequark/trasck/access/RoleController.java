package com.strangequark.trasck.access;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleManagementService roleManagementService;

    public RoleController(RoleManagementService roleManagementService) {
        this.roleManagementService = roleManagementService;
    }

    @GetMapping("/workspaces/{workspaceId}/roles")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public List<RoleResponse> listWorkspaceRoles(@PathVariable UUID workspaceId) {
        return roleManagementService.listWorkspaceRoles(workspaceId);
    }

    @GetMapping("/workspaces/{workspaceId}/roles/permissions")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public List<PermissionResponse> listWorkspaceRolePermissions(@PathVariable UUID workspaceId) {
        return roleManagementService.listPermissions();
    }

    @PostMapping("/workspaces/{workspaceId}/roles")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public ResponseEntity<RoleResponse> createWorkspaceRole(
            @PathVariable UUID workspaceId,
            @RequestBody RoleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleManagementService.createWorkspaceRole(workspaceId, request));
    }

    @GetMapping("/workspaces/{workspaceId}/roles/{roleId}")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public RoleResponse getWorkspaceRole(@PathVariable UUID workspaceId, @PathVariable UUID roleId) {
        return roleManagementService.getWorkspaceRole(workspaceId, roleId);
    }

    @PatchMapping("/workspaces/{workspaceId}/roles/{roleId}")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public RoleResponse updateWorkspaceRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId,
            @RequestBody RoleUpdateRequest request
    ) {
        return roleManagementService.updateWorkspaceRole(workspaceId, roleId, request);
    }

    @DeleteMapping("/workspaces/{workspaceId}/roles/{roleId}")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public ResponseEntity<Void> archiveWorkspaceRole(@PathVariable UUID workspaceId, @PathVariable UUID roleId) {
        roleManagementService.archiveWorkspaceRole(workspaceId, roleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/roles/{roleId}/permission-preview")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public RoleImpactPreviewResponse previewWorkspaceRolePermissions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId,
            @RequestBody RolePermissionPreviewRequest request
    ) {
        return roleManagementService.previewWorkspacePermissions(workspaceId, roleId, request);
    }

    @PutMapping("/workspaces/{workspaceId}/roles/{roleId}/permissions")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public RoleResponse updateWorkspaceRolePermissions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId,
            @RequestBody RolePermissionUpdateRequest request
    ) {
        return roleManagementService.updateWorkspacePermissions(workspaceId, roleId, request);
    }

    @GetMapping("/workspaces/{workspaceId}/roles/{roleId}/versions")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public List<RoleVersionResponse> listWorkspaceRoleVersions(@PathVariable UUID workspaceId, @PathVariable UUID roleId) {
        return roleManagementService.workspaceRoleVersions(workspaceId, roleId);
    }

    @PostMapping("/workspaces/{workspaceId}/roles/{roleId}/versions/{versionId}/rollback")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public RoleResponse rollbackWorkspaceRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID roleId,
            @PathVariable UUID versionId
    ) {
        return roleManagementService.rollbackWorkspaceRole(workspaceId, roleId, versionId);
    }

    @GetMapping("/projects/{projectId}/roles")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public List<RoleResponse> listProjectRoles(@PathVariable UUID projectId) {
        return roleManagementService.listProjectRoles(projectId);
    }

    @GetMapping("/projects/{projectId}/roles/permissions")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public List<PermissionResponse> listProjectRolePermissions(@PathVariable UUID projectId) {
        return roleManagementService.listPermissions();
    }

    @PostMapping("/projects/{projectId}/roles")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public ResponseEntity<RoleResponse> createProjectRole(
            @PathVariable UUID projectId,
            @RequestBody RoleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleManagementService.createProjectRole(projectId, request));
    }

    @GetMapping("/projects/{projectId}/roles/{roleId}")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public RoleResponse getProjectRole(@PathVariable UUID projectId, @PathVariable UUID roleId) {
        return roleManagementService.getProjectRole(projectId, roleId);
    }

    @PatchMapping("/projects/{projectId}/roles/{roleId}")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public RoleResponse updateProjectRole(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @RequestBody RoleUpdateRequest request
    ) {
        return roleManagementService.updateProjectRole(projectId, roleId, request);
    }

    @DeleteMapping("/projects/{projectId}/roles/{roleId}")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public ResponseEntity<Void> archiveProjectRole(@PathVariable UUID projectId, @PathVariable UUID roleId) {
        roleManagementService.archiveProjectRole(projectId, roleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/roles/{roleId}/permission-preview")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public RoleImpactPreviewResponse previewProjectRolePermissions(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @RequestBody RolePermissionPreviewRequest request
    ) {
        return roleManagementService.previewProjectPermissions(projectId, roleId, request);
    }

    @PutMapping("/projects/{projectId}/roles/{roleId}/permissions")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public RoleResponse updateProjectRolePermissions(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @RequestBody RolePermissionUpdateRequest request
    ) {
        return roleManagementService.updateProjectPermissions(projectId, roleId, request);
    }

    @GetMapping("/projects/{projectId}/roles/{roleId}/versions")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public List<RoleVersionResponse> listProjectRoleVersions(@PathVariable UUID projectId, @PathVariable UUID roleId) {
        return roleManagementService.projectRoleVersions(projectId, roleId);
    }

    @PostMapping("/projects/{projectId}/roles/{roleId}/versions/{versionId}/rollback")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public RoleResponse rollbackProjectRole(
            @PathVariable UUID projectId,
            @PathVariable UUID roleId,
            @PathVariable UUID versionId
    ) {
        return roleManagementService.rollbackProjectRole(projectId, roleId, versionId);
    }
}
