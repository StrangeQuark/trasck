package com.strangequark.trasck.team;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, ProjectTeamId> {
    boolean existsByIdProjectIdAndIdTeamId(UUID projectId, UUID teamId);

    List<ProjectTeam> findByIdProjectIdOrderByCreatedAtAsc(UUID projectId);

    List<ProjectTeam> findByIdTeamIdOrderByCreatedAtAsc(UUID teamId);
}
