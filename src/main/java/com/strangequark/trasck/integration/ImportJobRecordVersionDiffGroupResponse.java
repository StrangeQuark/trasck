package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportJobRecordVersionDiffGroupResponse(
        UUID recordId,
        String sourceType,
        String sourceId,
        String targetType,
        UUID targetId,
        String status,
        String conflictStatus,
        List<ImportJobRecordVersionDiffResponse> diffs
) {
    static ImportJobRecordVersionDiffGroupResponse from(
            ImportJobRecord record,
            List<ImportJobRecordVersionDiffResponse> diffs
    ) {
        return new ImportJobRecordVersionDiffGroupResponse(
                record.getId(),
                record.getSourceType(),
                record.getSourceId(),
                record.getTargetType(),
                record.getTargetId(),
                record.getStatus(),
                record.getConflictStatus(),
                diffs
        );
    }
}
