package com.strangequark.trasck.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByWorkspaceIdAndUserIdOrderByChannelAscEventTypeAsc(UUID workspaceId, UUID userId);

    Optional<NotificationPreference> findByIdAndUserId(UUID id, UUID userId);
}
