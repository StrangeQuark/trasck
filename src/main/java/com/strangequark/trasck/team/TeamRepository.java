package com.strangequark.trasck.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Team> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}
