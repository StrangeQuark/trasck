package com.strangequark.trasck.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {
    @Query("""
            select savedFilter
            from SavedFilter savedFilter
            where savedFilter.workspaceId = :workspaceId
              and (
                  savedFilter.ownerId = :userId
                  or savedFilter.visibility in ('team', 'project', 'workspace', 'public')
              )
            order by savedFilter.name asc
            """)
    List<SavedFilter> findVisibleCandidates(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
