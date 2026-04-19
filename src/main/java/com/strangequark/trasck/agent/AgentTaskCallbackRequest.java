package com.strangequark.trasck.agent;

import java.util.List;

public record AgentTaskCallbackRequest(
        String status,
        String message,
        Object resultPayload,
        List<AgentTaskCallbackArtifactRequest> artifacts,
        List<AgentTaskCallbackMessageRequest> messages
) {
}
