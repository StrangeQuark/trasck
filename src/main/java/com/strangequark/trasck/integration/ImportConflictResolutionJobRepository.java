package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportConflictResolutionJobRepository extends JpaRepository<ImportConflictResolutionJob, UUID> {
    List<ImportConflictResolutionJob> findByImportJobIdOrderByRequestedAtDesc(UUID importJobId);

    List<ImportConflictResolutionJob> findByWorkspaceIdOrderByRequestedAtDesc(UUID workspaceId);

    List<ImportConflictResolutionJob> findByWorkspaceIdAndStatusOrderByRequestedAtDesc(UUID workspaceId, String status);

    @Query("""
            select job
            from ImportConflictResolutionJob job
            where job.status = 'queued'
            order by job.requestedAt asc, job.id asc
            """)
    List<ImportConflictResolutionJob> findQueued(Pageable pageable);

    @Query("""
            select job
            from ImportConflictResolutionJob job
            where job.workspaceId = :workspaceId
              and job.status = 'queued'
            order by job.requestedAt asc, job.id asc
            """)
    List<ImportConflictResolutionJob> findQueuedByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);
}
