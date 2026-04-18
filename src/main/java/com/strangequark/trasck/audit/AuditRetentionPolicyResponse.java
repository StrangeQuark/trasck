package com.strangequark.trasck.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditRetentionPolicyResponse(
        UUID id,
        UUID workspaceId,
        Boolean retentionEnabled,
        Integer retentionDays,
        UUID updatedById,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static AuditRetentionPolicyResponse from(AuditRetentionPolicy policy) {
        return new AuditRetentionPolicyResponse(
                policy.getId(),
                policy.getWorkspaceId(),
                Boolean.TRUE.equals(policy.getRetentionEnabled()),
                policy.getRetentionDays(),
                policy.getUpdatedById(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }

    static AuditRetentionPolicyResponse permanent(UUID workspaceId) {
        return new AuditRetentionPolicyResponse(null, workspaceId, false, null, null, null, null);
    }
}
