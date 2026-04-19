package com.strangequark.trasck.team;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, ProjectTeamId> {
    boolean existsByIdProjectIdAndIdTeamId(UUID projectId, UUID teamId);
}
