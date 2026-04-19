package com.strangequark.trasck.team;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        UUID leadUserId,
        Integer defaultCapacity,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getWorkspaceId(),
                team.getName(),
                team.getDescription(),
                team.getLeadUserId(),
                team.getDefaultCapacity(),
                team.getStatus(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
