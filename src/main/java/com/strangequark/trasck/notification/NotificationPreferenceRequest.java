package com.strangequark.trasck.notification;

public record NotificationPreferenceRequest(
        String channel,
        String eventType,
        Boolean enabled,
        Object config
) {
}
