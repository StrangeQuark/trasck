package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentProfileResponse(
        UUID id,
        UUID workspaceId,
        UUID userId,
        UUID providerId,
        String displayName,
        String status,
        Integer maxConcurrentTasks,
        Object capabilities,
        Object config,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static AgentProfileResponse from(AgentProfile profile) {
        return new AgentProfileResponse(
                profile.getId(),
                profile.getWorkspaceId(),
                profile.getUserId(),
                profile.getProviderId(),
                profile.getDisplayName(),
                profile.getStatus(),
                profile.getMaxConcurrentTasks(),
                JsonValues.toJavaValue(profile.getCapabilities()),
                JsonValues.toJavaValue(profile.getConfig()),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
