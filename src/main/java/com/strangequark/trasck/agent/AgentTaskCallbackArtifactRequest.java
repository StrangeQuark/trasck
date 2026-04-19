package com.strangequark.trasck.agent;

public record AgentTaskCallbackArtifactRequest(
        String artifactType,
        String name,
        String externalUrl,
        Object metadata
) {
}
