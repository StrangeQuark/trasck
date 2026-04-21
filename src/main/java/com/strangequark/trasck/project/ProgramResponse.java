package com.strangequark.trasck.project;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProgramResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        String status,
        JsonNode roadmapConfig,
        JsonNode reportConfig,
        List<ProgramProjectResponse> projects,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ProgramResponse from(Program program, List<ProgramProject> projects) {
        return new ProgramResponse(
                program.getId(),
                program.getWorkspaceId(),
                program.getName(),
                program.getDescription(),
                program.getStatus(),
                program.getRoadmapConfig(),
                program.getReportConfig(),
                projects.stream().map(ProgramProjectResponse::from).toList(),
                program.getCreatedAt(),
                program.getUpdatedAt()
        );
    }
}
