package com.strangequark.trasck.access;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleRepository roleRepository;

    public RoleController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping("/workspaces/{workspaceId}/roles")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public List<RoleResponse> listWorkspaceRoles(@PathVariable UUID workspaceId) {
        return roleRepository.findByWorkspaceIdAndProjectIdIsNullOrderByNameAsc(workspaceId).stream()
                .map(RoleResponse::from)
                .toList();
    }

    @GetMapping("/projects/{projectId}/roles")
    @PreAuthorize("@permissionService.canUseProject(authentication, #projectId, 'project.admin')")
    public List<RoleResponse> listProjectRoles(@PathVariable UUID projectId) {
        return roleRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(RoleResponse::from)
                .toList();
    }
}
