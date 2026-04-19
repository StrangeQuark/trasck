package com.strangequark.trasck.search;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportQueryCatalogResponse(
        UUID id,
        UUID workspaceId,
        UUID ownerId,
        UUID projectId,
        UUID teamId,
        String queryKey,
        String name,
        String description,
        String queryType,
        Object queryConfig,
        Object parametersSchema,
        String visibility,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ReportQueryCatalogResponse from(ReportQueryCatalogEntry entry) {
        return new ReportQueryCatalogResponse(
                entry.getId(),
                entry.getWorkspaceId(),
                entry.getOwnerId(),
                entry.getProjectId(),
                entry.getTeamId(),
                entry.getQueryKey(),
                entry.getName(),
                entry.getDescription(),
                entry.getQueryType(),
                JsonValues.toJavaValue(entry.getQueryConfig()),
                JsonValues.toJavaValue(entry.getParametersSchema()),
                entry.getVisibility(),
                entry.getEnabled(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
