package com.strangequark.trasck.search;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentItemRepository extends JpaRepository<RecentItem, UUID> {
    List<RecentItem> findByUserIdOrderByViewedAtDesc(UUID userId);

    Optional<RecentItem> findByUserIdAndEntityTypeAndEntityId(UUID userId, String entityType, UUID entityId);
}
