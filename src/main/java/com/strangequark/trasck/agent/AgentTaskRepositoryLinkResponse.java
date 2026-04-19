package com.strangequark.trasck.agent;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AgentTaskRepositoryLinkResponse(
        UUID id,
        UUID agentTaskId,
        UUID repositoryConnectionId,
        String baseBranch,
        String workingBranch,
        String pullRequestUrl,
        Object metadata,
        OffsetDateTime createdAt
) {
    static AgentTaskRepositoryLinkResponse from(AgentTaskRepositoryLink link) {
        return new AgentTaskRepositoryLinkResponse(
                link.getId(),
                link.getAgentTaskId(),
                link.getRepositoryConnectionId(),
                link.getBaseBranch(),
                link.getWorkingBranch(),
                link.getPullRequestUrl(),
                JsonValues.toJavaValue(link.getMetadata()),
                link.getCreatedAt()
        );
    }
}
