package com.strangequark.trasck.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
