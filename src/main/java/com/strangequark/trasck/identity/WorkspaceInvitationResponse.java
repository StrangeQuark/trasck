package com.strangequark.trasck.identity;

import com.strangequark.trasck.access.Role;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceInvitationResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String email,
        UUID roleId,
        String roleKey,
        String roleName,
        UUID projectRoleId,
        String projectRoleKey,
        String projectRoleName,
        String status,
        UUID invitedById,
        UUID acceptedById,
        OffsetDateTime expiresAt,
        OffsetDateTime acceptedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static WorkspaceInvitationResponse from(UserInvitation invitation, Role workspaceRole, Role projectRole) {
        return new WorkspaceInvitationResponse(
                invitation.getId(),
                invitation.getWorkspaceId(),
                invitation.getProjectId(),
                invitation.getEmail(),
                invitation.getRoleId(),
                workspaceRole == null ? null : workspaceRole.getKey(),
                workspaceRole == null ? null : workspaceRole.getName(),
                invitation.getProjectRoleId(),
                projectRole == null ? null : projectRole.getKey(),
                projectRole == null ? null : projectRole.getName(),
                invitation.getStatus(),
                invitation.getInvitedById(),
                invitation.getAcceptedById(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getCreatedAt(),
                invitation.getUpdatedAt()
        );
    }
}
