package com.strangequark.trasck.agent;

import java.util.UUID;

public record AgentRuntimePreviewRequest(
        UUID agentProfileId,
        UUID workItemId,
        String action
) {
}
