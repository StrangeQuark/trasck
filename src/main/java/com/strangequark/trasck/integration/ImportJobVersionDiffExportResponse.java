package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;

public record ImportJobVersionDiffExportResponse(
        OffsetDateTime generatedAt,
        ImportJobResponse importJob,
        ImportJobVersionDiffResponse diffs
) {
}
