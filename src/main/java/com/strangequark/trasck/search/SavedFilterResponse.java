package com.strangequark.trasck.search;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SavedFilterResponse(
        UUID id,
        UUID workspaceId,
        UUID ownerId,
        UUID projectId,
        UUID teamId,
        String name,
        String visibility,
        Object query,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static SavedFilterResponse from(SavedFilter savedFilter) {
        return new SavedFilterResponse(
                savedFilter.getId(),
                savedFilter.getWorkspaceId(),
                savedFilter.getOwnerId(),
                savedFilter.getProjectId(),
                savedFilter.getTeamId(),
                savedFilter.getName(),
                savedFilter.getVisibility(),
                JsonValues.toJavaValue(savedFilter.getQuery()),
                savedFilter.getCreatedAt(),
                savedFilter.getUpdatedAt()
        );
    }
}
