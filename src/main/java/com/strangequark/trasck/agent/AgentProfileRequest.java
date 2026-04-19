package com.strangequark.trasck.agent;

import java.util.UUID;

public record AgentProfileRequest(
        UUID providerId,
        String displayName,
        String username,
        UUID roleId,
        String status,
        Integer maxConcurrentTasks,
        Object capabilities,
        Object config
) {
}
