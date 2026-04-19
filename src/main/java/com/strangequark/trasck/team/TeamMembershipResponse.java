package com.strangequark.trasck.team;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamMembershipResponse(
        UUID id,
        UUID teamId,
        UUID userId,
        String role,
        Integer capacityPercent,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt
) {
    static TeamMembershipResponse from(TeamMembership membership) {
        return new TeamMembershipResponse(
                membership.getId(),
                membership.getTeamId(),
                membership.getUserId(),
                membership.getRole(),
                membership.getCapacityPercent(),
                membership.getJoinedAt(),
                membership.getLeftAt()
        );
    }
}
