package com.strangequark.trasck.identity;

import com.strangequark.trasck.access.Role;
import com.strangequark.trasck.access.WorkspaceMembership;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID membershipId,
        UUID workspaceId,
        UUID userId,
        UUID roleId,
        String roleKey,
        String roleName,
        String status,
        OffsetDateTime invitedAt,
        OffsetDateTime joinedAt,
        OffsetDateTime createdAt,
        String email,
        String username,
        String displayName,
        String accountType,
        Boolean emailVerified,
        Boolean active,
        OffsetDateTime lastLoginAt
) {
    static WorkspaceMemberResponse from(WorkspaceMembership membership, User user, Role role) {
        return new WorkspaceMemberResponse(
                membership.getId(),
                membership.getWorkspaceId(),
                membership.getUserId(),
                membership.getRoleId(),
                role == null ? null : role.getKey(),
                role == null ? null : role.getName(),
                membership.getStatus(),
                membership.getInvitedAt(),
                membership.getJoinedAt(),
                membership.getCreatedAt(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                user == null ? null : user.getAccountType(),
                user == null ? null : user.getEmailVerified(),
                user == null ? null : user.getActive(),
                user == null ? null : user.getLastLoginAt()
        );
    }
}
