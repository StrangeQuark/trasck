package com.strangequark.trasck.project;

import java.util.UUID;

public record PublicProjectResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String key,
        String description,
        String visibility
) {
}
