package com.strangequark.trasck.agent;

public record AgentTaskHumanMessageRequest(
        String bodyMarkdown,
        Object bodyDocument
) {
}
