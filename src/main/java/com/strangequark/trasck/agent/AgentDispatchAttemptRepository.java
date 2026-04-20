package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentDispatchAttemptRepository extends JpaRepository<AgentDispatchAttempt, UUID> {
    List<AgentDispatchAttempt> findByAgentTaskIdOrderByStartedAtAscIdAsc(UUID agentTaskId);

    @Query(value = """
            select *
            from agent_dispatch_attempts
            where workspace_id = :workspaceId
              and (:agentTaskId is null or agent_task_id = :agentTaskId)
              and (:providerId is null or provider_id = :providerId)
              and (:agentProfileId is null or agent_profile_id = :agentProfileId)
              and (:workItemId is null or work_item_id = :workItemId)
              and (:attemptType is null or attempt_type = :attemptType)
              and (:status is null or status = :status)
              and (cast(:startedFrom as timestamptz) is null or started_at >= cast(:startedFrom as timestamptz))
              and (cast(:startedTo as timestamptz) is null or started_at <= cast(:startedTo as timestamptz))
              and (cast(:retentionCutoff as timestamptz) is null or started_at < cast(:retentionCutoff as timestamptz))
              and (
                  cast(:cursorStartedAt as timestamptz) is null
                  or started_at < cast(:cursorStartedAt as timestamptz)
                  or (started_at = cast(:cursorStartedAt as timestamptz) and id::text < :cursorId)
              )
            order by started_at desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<AgentDispatchAttempt> findFilteredPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("agentTaskId") UUID agentTaskId,
            @Param("providerId") UUID providerId,
            @Param("agentProfileId") UUID agentProfileId,
            @Param("workItemId") UUID workItemId,
            @Param("attemptType") String attemptType,
            @Param("status") String status,
            @Param("startedFrom") java.time.OffsetDateTime startedFrom,
            @Param("startedTo") java.time.OffsetDateTime startedTo,
            @Param("retentionCutoff") java.time.OffsetDateTime retentionCutoff,
            @Param("cursorStartedAt") java.time.OffsetDateTime cursorStartedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    @Query(value = """
            select count(*)
            from agent_dispatch_attempts
            where workspace_id = :workspaceId
              and (:agentTaskId is null or agent_task_id = :agentTaskId)
              and (:providerId is null or provider_id = :providerId)
              and (:agentProfileId is null or agent_profile_id = :agentProfileId)
              and (:workItemId is null or work_item_id = :workItemId)
              and (:attemptType is null or attempt_type = :attemptType)
              and (:status is null or status = :status)
              and (cast(:startedFrom as timestamptz) is null or started_at >= cast(:startedFrom as timestamptz))
              and (cast(:startedTo as timestamptz) is null or started_at <= cast(:startedTo as timestamptz))
              and (cast(:retentionCutoff as timestamptz) is null or started_at < cast(:retentionCutoff as timestamptz))
            """, nativeQuery = true)
    long countFiltered(
            @Param("workspaceId") UUID workspaceId,
            @Param("agentTaskId") UUID agentTaskId,
            @Param("providerId") UUID providerId,
            @Param("agentProfileId") UUID agentProfileId,
            @Param("workItemId") UUID workItemId,
            @Param("attemptType") String attemptType,
            @Param("status") String status,
            @Param("startedFrom") java.time.OffsetDateTime startedFrom,
            @Param("startedTo") java.time.OffsetDateTime startedTo,
            @Param("retentionCutoff") java.time.OffsetDateTime retentionCutoff
    );

    @Modifying
    @Query(value = """
            delete from agent_dispatch_attempts
            where id in (
                select id
                from agent_dispatch_attempts
                where workspace_id = :workspaceId
                  and (:agentTaskId is null or agent_task_id = :agentTaskId)
                  and (:providerId is null or provider_id = :providerId)
                  and (:agentProfileId is null or agent_profile_id = :agentProfileId)
                  and (:workItemId is null or work_item_id = :workItemId)
                  and (:attemptType is null or attempt_type = :attemptType)
                  and (:status is null or status = :status)
                  and (cast(:startedFrom as timestamptz) is null or started_at >= cast(:startedFrom as timestamptz))
                  and (cast(:startedTo as timestamptz) is null or started_at <= cast(:startedTo as timestamptz))
                  and started_at < :retentionCutoff
            )
            """, nativeQuery = true)
    int deleteFilteredBefore(
            @Param("workspaceId") UUID workspaceId,
            @Param("agentTaskId") UUID agentTaskId,
            @Param("providerId") UUID providerId,
            @Param("agentProfileId") UUID agentProfileId,
            @Param("workItemId") UUID workItemId,
            @Param("attemptType") String attemptType,
            @Param("status") String status,
            @Param("startedFrom") java.time.OffsetDateTime startedFrom,
            @Param("startedTo") java.time.OffsetDateTime startedTo,
            @Param("retentionCutoff") java.time.OffsetDateTime retentionCutoff
    );
}
