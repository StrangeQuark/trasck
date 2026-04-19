package com.strangequark.trasck.agent;

import java.util.UUID;

public record RepositoryConnectionRequest(
        UUID projectId,
        String provider,
        String name,
        String repositoryUrl,
        String defaultBranch,
        Object providerMetadata,
        Object config,
        Boolean active
) {
}
