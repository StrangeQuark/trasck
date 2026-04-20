package com.strangequark.trasck.integration;

import java.util.UUID;

public record ImportReviewCsvExportJobRequest(
        String tableType,
        UUID importJobId,
        UUID projectId,
        String status,
        String exportType,
        String filterColumn,
        String filter
) {
}
