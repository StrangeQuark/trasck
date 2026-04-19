package com.strangequark.trasck.team;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectTeamResponse(
        UUID projectId,
        UUID teamId,
        String role,
        OffsetDateTime createdAt
) {
    static ProjectTeamResponse from(ProjectTeam projectTeam) {
        return new ProjectTeamResponse(
                projectTeam.getId().getProjectId(),
                projectTeam.getId().getTeamId(),
                projectTeam.getRole(),
                projectTeam.getCreatedAt()
        );
    }
}
