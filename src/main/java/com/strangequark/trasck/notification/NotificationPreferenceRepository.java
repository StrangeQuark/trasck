package com.strangequark.trasck.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByWorkspaceIdAndUserIdOrderByChannelAscEventTypeAsc(UUID workspaceId, UUID userId);

    Optional<NotificationPreference> findByWorkspaceIdAndUserIdAndChannelAndEventType(UUID workspaceId, UUID userId, String channel, String eventType);

    Optional<NotificationPreference> findByIdAndUserId(UUID id, UUID userId);

    List<NotificationPreference> findByWorkspaceIdAndUserIdIsNullOrderByChannelAscEventTypeAsc(UUID workspaceId);

    Optional<NotificationPreference> findByWorkspaceIdAndUserIdIsNullAndChannelAndEventType(UUID workspaceId, String channel, String eventType);

    Optional<NotificationPreference> findByIdAndUserIdIsNull(UUID id);
}
