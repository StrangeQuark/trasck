package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;

public record AgentProfileRequest(
        UUID providerId,
        String displayName,
        String username,
        UUID roleId,
        List<UUID> projectIds,
        String status,
        Integer maxConcurrentTasks,
        Object capabilities,
        Object config
) {
}
