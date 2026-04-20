package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ImportJobRecordVersionDiffResponse(
        UUID versionId,
        UUID importJobRecordId,
        UUID importJobId,
        Integer version,
        Integer comparedToVersion,
        String changeType,
        UUID changedById,
        OffsetDateTime createdAt,
        List<ImportJobRecordFieldDiffResponse> fields
) {
}
