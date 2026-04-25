package com.strangequark.trasck.project;

import com.strangequark.trasck.setup.InitialSetupResponse;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID workspaceId,
        UUID parentProjectId,
        String name,
        String key,
        String description,
        String visibility,
        String status,
        UUID leadUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        InitialSetupResponse.SeedDataSummary seedData
) {
    static ProjectResponse from(Project project) {
        return from(project, null);
    }

    static ProjectResponse from(Project project, InitialSetupResponse.SeedDataSummary seedData) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getParentProjectId(),
                project.getName(),
                project.getKey(),
                project.getDescription(),
                project.getVisibility(),
                project.getStatus(),
                project.getLeadUserId(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                seedData
        );
    }
}
