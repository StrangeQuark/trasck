package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportConflictBulkResolutionResponse(
        UUID importJobId,
        String resolution,
        Integer requested,
        Integer resolved,
        List<ImportJobRecordResponse> records
) {
}
