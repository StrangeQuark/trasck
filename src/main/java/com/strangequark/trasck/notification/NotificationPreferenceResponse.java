package com.strangequark.trasck.notification;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        UUID userId,
        UUID workspaceId,
        String channel,
        String eventType,
        Boolean enabled,
        Object config
) {
    static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.getId(),
                preference.getUserId(),
                preference.getWorkspaceId(),
                preference.getChannel(),
                preference.getEventType(),
                preference.getEnabled(),
                JsonValues.toJavaValue(preference.getConfig())
        );
    }
}
