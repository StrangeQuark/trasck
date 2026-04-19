package com.strangequark.trasck.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {
    @Query("""
            select dashboard
            from Dashboard dashboard
            where dashboard.workspaceId = :workspaceId
              and (
                  dashboard.ownerId = :userId
                  or dashboard.visibility in ('workspace', 'public')
                  or dashboard.visibility = 'team'
              )
            order by dashboard.name asc
            """)
    List<Dashboard> findVisibleCandidates(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
