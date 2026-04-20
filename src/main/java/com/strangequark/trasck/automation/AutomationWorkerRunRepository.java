package com.strangequark.trasck.automation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationWorkerRunRepository extends JpaRepository<AutomationWorkerRun, UUID> {
    List<AutomationWorkerRun> findTop50ByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId);

    List<AutomationWorkerRun> findTop50ByWorkspaceIdAndWorkerTypeOrderByStartedAtDesc(UUID workspaceId, String workerType);

    long countByWorkspaceIdAndStartedAtBefore(UUID workspaceId, OffsetDateTime cutoff);

    List<AutomationWorkerRun> findByWorkspaceIdAndStartedAtBeforeOrderByStartedAtAsc(UUID workspaceId, OffsetDateTime cutoff, Pageable pageable);

    @Modifying
    @Query("""
            delete from AutomationWorkerRun run
            where run.workspaceId = :workspaceId
              and run.startedAt < :cutoff
            """)
    int deleteRetainedRuns(@Param("workspaceId") UUID workspaceId, @Param("cutoff") OffsetDateTime cutoff);
}
