package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentMessageResponse(
        UUID id,
        UUID agentTaskId,
        UUID senderUserId,
        String senderType,
        String bodyMarkdown,
        Object bodyDocument,
        OffsetDateTime createdAt
) {
    static AgentMessageResponse from(AgentMessage message) {
        return new AgentMessageResponse(
                message.getId(),
                message.getAgentTaskId(),
                message.getSenderUserId(),
                message.getSenderType(),
                message.getBodyMarkdown(),
                JsonValues.toJavaValue(message.getBodyDocument()),
                message.getCreatedAt()
        );
    }
}
