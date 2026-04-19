package com.strangequark.trasck.agent;

import java.util.List;

public record AgentWorkerClaimRequest(
        String workerId,
        List<String> capabilities,
        Integer maxTasks,
        Object metadata
) {
}
