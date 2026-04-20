package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportJobRecordResponse(
        UUID id,
        UUID importJobId,
        String sourceType,
        String sourceId,
        String targetType,
        UUID targetId,
        String status,
        String errorMessage,
        Object rawPayload,
        String conflictStatus,
        String conflictReason,
        OffsetDateTime conflictDetectedAt,
        OffsetDateTime conflictResolvedAt,
        String conflictResolution,
        UUID conflictResolvedById,
        UUID conflictMaterializationRunId
) {
    static ImportJobRecordResponse from(ImportJobRecord record) {
        return new ImportJobRecordResponse(
                record.getId(),
                record.getImportJobId(),
                record.getSourceType(),
                record.getSourceId(),
                record.getTargetType(),
                record.getTargetId(),
                record.getStatus(),
                record.getErrorMessage(),
                JsonValues.toJavaValue(record.getRawPayload()),
                record.getConflictStatus(),
                record.getConflictReason(),
                record.getConflictDetectedAt(),
                record.getConflictResolvedAt(),
                record.getConflictResolution(),
                record.getConflictResolvedById(),
                record.getConflictMaterializationRunId()
        );
    }
}
