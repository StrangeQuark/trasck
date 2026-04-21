package com.strangequark.trasck.notification;

import java.util.UUID;

public record NotificationRequest(
        UUID userId,
        String type,
        String title,
        String body,
        String targetType,
        UUID targetId
) {
}
