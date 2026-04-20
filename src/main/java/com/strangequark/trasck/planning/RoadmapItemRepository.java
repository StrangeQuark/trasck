package com.strangequark.trasck.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, UUID> {
    List<RoadmapItem> findByRoadmapIdOrderByPositionAsc(UUID roadmapId);

    Optional<RoadmapItem> findByIdAndRoadmapId(UUID id, UUID roadmapId);
}
