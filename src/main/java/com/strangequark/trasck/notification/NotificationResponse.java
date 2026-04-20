package com.strangequark.trasck.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        UUID actorId,
        UUID workspaceId,
        String type,
        String title,
        String body,
        String targetType,
        UUID targetId,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getActorId(),
                notification.getWorkspaceId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
