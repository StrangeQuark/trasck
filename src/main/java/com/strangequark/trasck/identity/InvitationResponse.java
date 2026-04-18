package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String email,
        UUID roleId,
        UUID projectRoleId,
        String token,
        String status,
        OffsetDateTime expiresAt
) {
    static InvitationResponse from(UserInvitation invitation, String token) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getWorkspaceId(),
                invitation.getProjectId(),
                invitation.getEmail(),
                invitation.getRoleId(),
                invitation.getProjectRoleId(),
                token,
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }
}
