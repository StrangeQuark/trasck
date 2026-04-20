package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportConflictBulkResolutionPreviewResponse(
        UUID importJobId,
        String resolution,
        String scope,
        Integer matched,
        Integer returned,
        Integer page,
        Integer pageSize,
        Boolean hasMore,
        Integer maxResolutionBatchSize,
        List<ImportJobRecordResponse> records
) {
}
