package com.strangequark.trasck.audit;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogEntryResponse(
        UUID id,
        UUID domainEventId,
        UUID workspaceId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        Object beforeValue,
        Object afterValue,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt
) {
    static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
                entry.getId(),
                entry.getDomainEventId(),
                entry.getWorkspaceId(),
                entry.getActorId(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                JsonValues.toJavaValue(entry.getBeforeValue()),
                JsonValues.toJavaValue(entry.getAfterValue()),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getCreatedAt()
        );
    }
}
