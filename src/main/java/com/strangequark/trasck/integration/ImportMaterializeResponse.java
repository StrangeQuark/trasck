package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportMaterializeResponse(
        UUID materializationRunId,
        UUID importJobId,
        UUID mappingTemplateId,
        UUID projectId,
        int recordsProcessed,
        int created,
        int updated,
        int failed,
        List<ImportJobRecordResponse> records
) {
}
