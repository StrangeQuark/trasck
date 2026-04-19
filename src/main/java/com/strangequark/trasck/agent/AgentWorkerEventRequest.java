package com.strangequark.trasck.agent;

public record AgentWorkerEventRequest(
        String workerId,
        String eventType,
        String severity,
        String message,
        Object metadata
) {
}
