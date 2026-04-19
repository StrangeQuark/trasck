package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;

public record AgentTaskAssignRequest(
        UUID agentProfileId,
        List<UUID> repositoryConnectionIds,
        Object requestPayload
) {
}
