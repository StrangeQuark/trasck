package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RepositoryConnectionResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String provider,
        String name,
        String repositoryUrl,
        String defaultBranch,
        Object config,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static RepositoryConnectionResponse from(RepositoryConnection connection) {
        return new RepositoryConnectionResponse(
                connection.getId(),
                connection.getWorkspaceId(),
                connection.getProjectId(),
                connection.getProvider(),
                connection.getName(),
                connection.getRepositoryUrl(),
                connection.getDefaultBranch(),
                JsonValues.toJavaValue(connection.getConfig()),
                connection.getActive(),
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }
}
