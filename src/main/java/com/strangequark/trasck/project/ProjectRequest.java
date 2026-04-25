package com.strangequark.trasck.project;

import java.util.UUID;

public record ProjectRequest(
        String name,
        String key,
        String description,
        String visibility,
        String status,
        UUID parentProjectId,
        UUID leadUserId
) {
}
