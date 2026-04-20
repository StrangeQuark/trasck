package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ImportJobResponse(
        UUID id,
        UUID workspaceId,
        UUID requestedById,
        String provider,
        String status,
        Object config,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        List<ImportJobRecordResponse> records
) {
    static ImportJobResponse from(ImportJob job, List<ImportJobRecord> records) {
        return new ImportJobResponse(
                job.getId(),
                job.getWorkspaceId(),
                job.getRequestedById(),
                job.getProvider(),
                job.getStatus(),
                JsonValues.toJavaValue(job.getConfig()),
                job.getStartedAt(),
                job.getFinishedAt(),
                records.stream().map(ImportJobRecordResponse::from).toList()
        );
    }
}
