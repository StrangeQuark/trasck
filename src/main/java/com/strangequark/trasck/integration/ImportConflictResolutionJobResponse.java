package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportConflictResolutionJobResponse(
        UUID id,
        UUID workspaceId,
        UUID importJobId,
        UUID requestedById,
        String resolution,
        String scope,
        String status,
        String statusFilter,
        String conflictStatusFilter,
        String sourceTypeFilter,
        Integer expectedCount,
        Integer matchedCount,
        Integer resolvedCount,
        Integer failedCount,
        String errorMessage,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    static ImportConflictResolutionJobResponse from(ImportConflictResolutionJob job) {
        return new ImportConflictResolutionJobResponse(
                job.getId(),
                job.getWorkspaceId(),
                job.getImportJobId(),
                job.getRequestedById(),
                job.getResolution(),
                job.getScope(),
                job.getStatus(),
                job.getStatusFilter(),
                job.getConflictStatusFilter(),
                job.getSourceTypeFilter(),
                job.getExpectedCount(),
                job.getMatchedCount(),
                job.getResolvedCount(),
                job.getFailedCount(),
                job.getErrorMessage(),
                job.getRequestedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
