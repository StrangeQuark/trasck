package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportReviewCsvExportWorkerResponse(
        UUID workspaceId,
        int requestedLimit,
        int processed,
        int completed,
        int failed,
        List<ExportJobResponse> jobs
) {
}
