package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentArtifactResponse(
        UUID id,
        UUID agentTaskId,
        String artifactType,
        String name,
        String externalUrl,
        Object metadata,
        OffsetDateTime createdAt
) {
    static AgentArtifactResponse from(AgentArtifact artifact) {
        return new AgentArtifactResponse(
                artifact.getId(),
                artifact.getAgentTaskId(),
                artifact.getArtifactType(),
                artifact.getName(),
                artifact.getExternalUrl(),
                JsonValues.toJavaValue(artifact.getMetadata()),
                artifact.getCreatedAt()
        );
    }
}
