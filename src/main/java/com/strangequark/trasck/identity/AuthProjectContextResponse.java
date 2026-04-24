package com.strangequark.trasck.identity;

import com.strangequark.trasck.access.ProjectMembership;
import com.strangequark.trasck.project.Project;
import java.util.UUID;

public record AuthProjectContextResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String key,
        String visibility,
        String status,
        UUID membershipId,
        UUID roleId,
        String membershipStatus
) {
    static AuthProjectContextResponse from(Project project, ProjectMembership membership) {
        return new AuthProjectContextResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getKey(),
                project.getVisibility(),
                project.getStatus(),
                membership.getId(),
                membership.getRoleId(),
                membership.getStatus()
        );
    }
}
