package com.strangequark.trasck.planning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {
    List<Roadmap> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<Roadmap> findByProjectIdOrderByNameAsc(UUID projectId);
}
