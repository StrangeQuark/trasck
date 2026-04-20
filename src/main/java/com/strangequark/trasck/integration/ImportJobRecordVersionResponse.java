package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportJobRecordVersionResponse(
        UUID id,
        UUID importJobRecordId,
        UUID importJobId,
        Integer version,
        String changeType,
        UUID changedById,
        Object snapshot,
        OffsetDateTime createdAt
) {
    static ImportJobRecordVersionResponse from(ImportJobRecordVersion version) {
        return new ImportJobRecordVersionResponse(
                version.getId(),
                version.getImportJobRecordId(),
                version.getImportJobId(),
                version.getVersion(),
                version.getChangeType(),
                version.getChangedById(),
                JsonValues.toJavaValue(version.getSnapshot()),
                version.getCreatedAt()
        );
    }
}
