package com.strangequark.trasck.access;

public record RoleImpactSummary(
        long activeMembers,
        long pendingInvitations,
        long affectedUsers,
        boolean affectsCurrentUser
) {
}
