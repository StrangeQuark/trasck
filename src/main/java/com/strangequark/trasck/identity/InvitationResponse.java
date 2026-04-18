package com.strangequark.trasck.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID workspaceId,
        String email,
        UUID roleId,
        String token,
        String status,
        OffsetDateTime expiresAt
) {
    static InvitationResponse from(UserInvitation invitation, String token) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getWorkspaceId(),
                invitation.getEmail(),
                invitation.getRoleId(),
                token,
                invitation.getStatus(),
                invitation.getExpiresAt()
        );
    }
}
