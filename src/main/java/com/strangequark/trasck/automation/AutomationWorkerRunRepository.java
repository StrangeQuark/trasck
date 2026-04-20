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

    long countByWorkspaceIdAndWorkerTypeAndStartedAtBefore(UUID workspaceId, String workerType, OffsetDateTime cutoff);

    List<AutomationWorkerRun> findByWorkspaceIdAndStartedAtBeforeOrderByStartedAtAsc(UUID workspaceId, OffsetDateTime cutoff, Pageable pageable);

    List<AutomationWorkerRun> findByWorkspaceIdAndWorkerTypeAndStartedAtBeforeOrderByStartedAtAsc(
            UUID workspaceId,
            String workerType,
            OffsetDateTime cutoff,
            Pageable pageable
    );

    @Query(value = """
            select count(*)
            from automation_worker_runs
            where workspace_id = :workspaceId
              and started_at < :cutoff
              and (:workerType is null or worker_type = :workerType)
              and (:triggerType is null or trigger_type = :triggerType)
              and (:status is null or status = :status)
              and (cast(:startedFrom as timestamptz) is null or started_at >= cast(:startedFrom as timestamptz))
              and (cast(:startedTo as timestamptz) is null or started_at <= cast(:startedTo as timestamptz))
            """, nativeQuery = true)
    long countRetainedRunsFiltered(
            @Param("workspaceId") UUID workspaceId,
            @Param("workerType") String workerType,
            @Param("triggerType") String triggerType,
            @Param("status") String status,
            @Param("startedFrom") OffsetDateTime startedFrom,
            @Param("startedTo") OffsetDateTime startedTo,
            @Param("cutoff") OffsetDateTime cutoff
    );

    @Query(value = """
            select *
            from automation_worker_runs
            where workspace_id = :workspaceId
              and started_at < :cutoff
              and (:workerType is null or worker_type = :workerType)
              and (:triggerType is null or trigger_type = :triggerType)
              and (:status is null or status = :status)
              and (cast(:startedFrom as timestamptz) is null or started_at >= cast(:startedFrom as timestamptz))
              and (cast(:startedTo as timestamptz) is null or started_at <= cast(:startedTo as timestamptz))
            order by started_at asc, id asc
            limit :limit
            """, nativeQuery = true)
    List<AutomationWorkerRun> findRetainedRunsFiltered(
            @Param("workspaceId") UUID workspaceId,
            @Param("workerType") String workerType,
            @Param("triggerType") String triggerType,
            @Param("status") String status,
            @Param("startedFrom") OffsetDateTime startedFrom,
            @Param("startedTo") OffsetDateTime startedTo,
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("limit") int limit
    );

    @Modifying
    @Query("""
            delete from AutomationWorkerRun run
            where run.workspaceId = :workspaceId
              and run.startedAt < :cutoff
            """)
    int deleteRetainedRuns(@Param("workspaceId") UUID workspaceId, @Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query("""
            delete from AutomationWorkerRun run
            where run.workspaceId = :workspaceId
              and run.workerType = :workerType
              and run.startedAt < :cutoff
            """)
    int deleteRetainedRunsByWorkerType(
            @Param("workspaceId") UUID workspaceId,
            @Param("workerType") String workerType,
            @Param("cutoff") OffsetDateTime cutoff
    );

    @Modifying
    @Query(value = """
            delete from automation_worker_runs
            where id in (
                select id
                from automation_worker_runs
                where workspace_id = :workspaceId
                  and started_at < :cutoff
                  and (:workerType is null or worker_type = :workerType)
                  and (:triggerType is null or trigger_type = :triggerType)
                  and (:status is null or status = :status)
                  and (cast(:startedFrom as timestamptz) is null or started_at >= cast(:startedFrom as timestamptz))
                  and (cast(:startedTo as timestamptz) is null or started_at <= cast(:startedTo as timestamptz))
            )
            """, nativeQuery = true)
    int deleteRetainedRunsFiltered(
            @Param("workspaceId") UUID workspaceId,
            @Param("workerType") String workerType,
            @Param("triggerType") String triggerType,
            @Param("status") String status,
            @Param("startedFrom") OffsetDateTime startedFrom,
            @Param("startedTo") OffsetDateTime startedTo,
            @Param("cutoff") OffsetDateTime cutoff
    );
}
