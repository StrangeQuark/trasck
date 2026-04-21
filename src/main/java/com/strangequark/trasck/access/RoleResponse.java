package com.strangequark.trasck.access;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String key,
        String name,
        String scope,
        String description,
        Boolean systemRole,
        String status,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<String> permissionKeys,
        RoleImpactSummary impactSummary
) {
    static RoleResponse from(Role role) {
        return from(role, List.of(), null);
    }

    static RoleResponse from(Role role, List<String> permissionKeys, RoleImpactSummary impactSummary) {
        return new RoleResponse(
                role.getId(),
                role.getWorkspaceId(),
                role.getProjectId(),
                role.getKey(),
                role.getName(),
                role.getScope(),
                role.getDescription(),
                role.getSystemRole(),
                role.getStatus(),
                role.getArchivedAt(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                permissionKeys,
                impactSummary
        );
    }
}
