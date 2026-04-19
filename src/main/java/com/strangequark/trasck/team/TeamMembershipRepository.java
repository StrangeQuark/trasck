package com.strangequark.trasck.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, UUID> {
    boolean existsByTeamIdAndUserIdAndLeftAtIsNull(UUID teamId, UUID userId);

    Optional<TeamMembership> findByTeamIdAndUserId(UUID teamId, UUID userId);

    List<TeamMembership> findByTeamIdOrderByJoinedAtAsc(UUID teamId);

    List<TeamMembership> findByTeamIdAndLeftAtIsNullOrderByJoinedAtAsc(UUID teamId);
}
