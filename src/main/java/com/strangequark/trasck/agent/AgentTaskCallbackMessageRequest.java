package com.strangequark.trasck.agent;

public record AgentTaskCallbackMessageRequest(
        String senderType,
        String bodyMarkdown,
        Object bodyDocument
) {
}
