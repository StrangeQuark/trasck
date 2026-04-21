package com.strangequark.trasck.access;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleVersionResponse(
        UUID id,
        UUID roleId,
        UUID workspaceId,
        UUID projectId,
        Integer versionNumber,
        String name,
        String key,
        String scope,
        String description,
        Boolean systemRole,
        String status,
        Object permissionKeys,
        String changeType,
        String changeNote,
        UUID createdById,
        OffsetDateTime createdAt
) {
    static RoleVersionResponse from(RoleVersion version) {
        return new RoleVersionResponse(
                version.getId(),
                version.getRoleId(),
                version.getWorkspaceId(),
                version.getProjectId(),
                version.getVersionNumber(),
                version.getName(),
                version.getKey(),
                version.getScope(),
                version.getDescription(),
                version.getSystemRole(),
                version.getStatus(),
                JsonValues.toJavaValue(version.getPermissionKeys()),
                version.getChangeType(),
                version.getChangeNote(),
                version.getCreatedById(),
                version.getCreatedAt()
        );
    }
}
