package com.strangequark.trasck.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        UUID organizationId,
        String name,
        String key,
        String timezone,
        String locale,
        String status,
        Boolean anonymousReadEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getOrganizationId(),
                workspace.getName(),
                workspace.getKey(),
                workspace.getTimezone(),
                workspace.getLocale(),
                workspace.getStatus(),
                workspace.getAnonymousReadEnabled(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt()
        );
    }
}
