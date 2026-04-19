package com.strangequark.trasck.search;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByUserIdAndEntityTypeAndEntityId(UUID userId, String entityType, UUID entityId);
}
