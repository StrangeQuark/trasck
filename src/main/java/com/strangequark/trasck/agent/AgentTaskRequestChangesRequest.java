package com.strangequark.trasck.agent;

public record AgentTaskRequestChangesRequest(
        String message,
        Object requestPayload
) {
}
