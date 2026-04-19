package com.strangequark.trasck.agent;

public record AgentWorkerHeartbeatRequest(
        String workerId,
        String status,
        String message,
        Object metadata
) {
}
