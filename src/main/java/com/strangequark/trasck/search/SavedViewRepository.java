package com.strangequark.trasck.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {
    @Query("""
            select savedView
            from SavedView savedView
            where savedView.workspaceId = :workspaceId
              and (
                  savedView.ownerId = :userId
                  or savedView.visibility in ('team', 'project', 'workspace', 'public')
              )
            order by savedView.name asc
            """)
    List<SavedView> findVisibleCandidates(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    List<SavedView> findByWorkspaceIdAndVisibilityAndProjectIdOrderByNameAsc(UUID workspaceId, String visibility, UUID projectId);

    List<SavedView> findByWorkspaceIdAndVisibilityAndTeamIdOrderByNameAsc(UUID workspaceId, String visibility, UUID teamId);
}
