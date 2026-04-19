package com.strangequark.trasck.search;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportQueryCatalogRepository extends JpaRepository<ReportQueryCatalogEntry, UUID> {
    boolean existsByWorkspaceIdAndQueryKeyIgnoreCase(UUID workspaceId, String queryKey);

    Optional<ReportQueryCatalogEntry> findByWorkspaceIdAndQueryKeyIgnoreCase(UUID workspaceId, String queryKey);

    @Query("""
            select entry
            from ReportQueryCatalogEntry entry
            where entry.workspaceId = :workspaceId
              and (
                  entry.ownerId = :userId
                  or entry.visibility in ('team', 'project', 'workspace', 'public')
              )
            order by entry.name asc
            """)
    List<ReportQueryCatalogEntry> findVisibleCandidates(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    List<ReportQueryCatalogEntry> findByWorkspaceIdAndVisibilityAndProjectIdOrderByNameAsc(UUID workspaceId, String visibility, UUID projectId);

    List<ReportQueryCatalogEntry> findByWorkspaceIdAndVisibilityAndTeamIdOrderByNameAsc(UUID workspaceId, String visibility, UUID teamId);
}
