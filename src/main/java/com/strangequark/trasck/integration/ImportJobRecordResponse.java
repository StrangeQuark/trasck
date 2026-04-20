package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
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
        Object rawPayload
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
                JsonValues.toJavaValue(record.getRawPayload())
        );
    }
}
