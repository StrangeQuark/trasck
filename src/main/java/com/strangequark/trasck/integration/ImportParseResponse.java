package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportParseResponse(
        UUID importJobId,
        String provider,
        int recordsParsed,
        List<ImportJobRecordResponse> records
) {
}
