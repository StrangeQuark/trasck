package com.strangequark.trasck.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectMembership> findByProjectIdAndUserIdAndStatusIgnoreCase(UUID projectId, UUID userId, String status);

    boolean existsByProjectIdAndUserIdAndStatusIgnoreCase(UUID projectId, UUID userId, String status);

    long countByProjectIdAndRoleIdAndStatusIgnoreCase(UUID projectId, UUID roleId, String status);

    List<ProjectMembership> findByProjectIdAndStatusIgnoreCaseOrderByCreatedAtDesc(UUID projectId, String status);

    @Query("""
            select membership
            from ProjectMembership membership
            join Project project on project.id = membership.projectId
            where project.workspaceId = :workspaceId
              and membership.userId = :userId
              and lower(membership.status) = lower(:status)
            """)
    List<ProjectMembership> findByWorkspaceIdAndUserIdAndStatusIgnoreCase(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("status") String status
    );
}
