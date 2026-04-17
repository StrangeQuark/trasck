package com.strangequark.trasck.planning;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {
}
