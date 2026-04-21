package com.strangequark.trasck.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProgramProjectResponse(
        UUID programId,
        UUID projectId,
        Integer position,
        OffsetDateTime createdAt
) {
    static ProgramProjectResponse from(ProgramProject programProject) {
        return new ProgramProjectResponse(
                programProject.getId().getProgramId(),
                programProject.getId().getProjectId(),
                programProject.getPosition(),
                programProject.getCreatedAt()
        );
    }
}
