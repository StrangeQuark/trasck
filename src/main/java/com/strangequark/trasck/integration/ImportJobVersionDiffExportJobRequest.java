package com.strangequark.trasck.integration;

public record ImportJobVersionDiffExportJobRequest(
        String format,
        String filterColumn,
        String filter
) {
}
