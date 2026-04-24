package com.strangequark.trasck.identity;

import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.workspace.Workspace;
import java.util.UUID;

public record AuthWorkspaceContextResponse(
        UUID id,
        String name,
        String key,
        String status,
        UUID membershipId,
        UUID roleId,
        String membershipStatus
) {
    static AuthWorkspaceContextResponse from(Workspace workspace, WorkspaceMembership membership) {
        return new AuthWorkspaceContextResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getKey(),
                workspace.getStatus(),
                membership.getId(),
                membership.getRoleId(),
                membership.getStatus()
        );
    }
}
