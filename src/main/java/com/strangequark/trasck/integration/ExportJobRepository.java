package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
    @Query(value = """
            select *
            from export_jobs
            where workspace_id = :workspaceId
            order by coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<ExportJob> findFirstCursorPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from export_jobs
            where workspace_id = :workspaceId
              and export_type = :exportType
            order by coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<ExportJob> findFirstCursorPageByExportType(
            @Param("workspaceId") UUID workspaceId,
            @Param("exportType") String exportType,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from export_jobs
            where workspace_id = :workspaceId
              and (
                  coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') < :cursorStartedAt
                  or (coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') = :cursorStartedAt and id::text < :cursorId)
              )
            order by coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<ExportJob> findCursorPageAfter(
            @Param("workspaceId") UUID workspaceId,
            @Param("cursorStartedAt") OffsetDateTime cursorStartedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from export_jobs
            where workspace_id = :workspaceId
              and export_type = :exportType
              and (
                  coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') < :cursorStartedAt
                  or (coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') = :cursorStartedAt and id::text < :cursorId)
              )
            order by coalesce(started_at, created_at, timestamptz '0001-01-01 00:00:00+00') desc, id desc
            limit :limit
            """, nativeQuery = true)
    List<ExportJob> findCursorPageAfterByExportType(
            @Param("workspaceId") UUID workspaceId,
            @Param("exportType") String exportType,
            @Param("cursorStartedAt") OffsetDateTime cursorStartedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    @Query(value = """
            select *
            from export_jobs
            where workspace_id = :workspaceId
              and status = 'queued'
              and export_type in (
                  'import_conflict_resolution_jobs',
                  'import_export_jobs',
                  'import_project_completion',
                  'import_workspace_completion'
              )
            order by created_at asc, id asc
            limit :limit
            """, nativeQuery = true)
    List<ExportJob> findQueuedImportReviewExportJobs(
            @Param("workspaceId") UUID workspaceId,
            @Param("limit") int limit
    );
}
