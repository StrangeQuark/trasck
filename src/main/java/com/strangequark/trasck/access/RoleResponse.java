package com.strangequark.trasck.access;

import java.util.UUID;

public record RoleResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String key,
        String name,
        String scope,
        String description,
        Boolean systemRole
) {
    static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getWorkspaceId(),
                role.getProjectId(),
                role.getKey(),
                role.getName(),
                role.getScope(),
                role.getDescription(),
                role.getSystemRole()
        );
    }
}
