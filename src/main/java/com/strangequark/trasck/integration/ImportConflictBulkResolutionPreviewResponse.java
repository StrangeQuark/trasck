package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportConflictBulkResolutionPreviewResponse(
        UUID importJobId,
        String resolution,
        String scope,
        Integer matched,
        List<ImportJobRecordResponse> records
) {
}
