package com.strangequark.trasck.team;

import java.util.UUID;

public record TeamMembershipRequest(
        UUID userId,
        String role,
        Integer capacityPercent
) {
}
