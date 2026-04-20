package com.strangequark.trasck.automation;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationExecutionJobRepository extends JpaRepository<AutomationExecutionJob, UUID> {
    List<AutomationExecutionJob> findByRuleIdOrderByCreatedAtDesc(UUID ruleId);

    @Query(value = """
            select *
            from automation_execution_jobs
            where workspace_id = :workspaceId
              and (
                  status = 'queued'
                  or (status = 'failed' and next_attempt_at is not null and next_attempt_at <= :now)
              )
            order by created_at asc
            limit :limit
            """, nativeQuery = true)
    List<AutomationExecutionJob> findProcessableJobs(
            @Param("workspaceId") UUID workspaceId,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );
}
