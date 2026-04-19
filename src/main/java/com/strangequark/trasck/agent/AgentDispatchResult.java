package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentDispatchResult(
        String externalTaskId,
        JsonNode dispatchPayload
) {
}
