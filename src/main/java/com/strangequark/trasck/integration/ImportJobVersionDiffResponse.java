package com.strangequark.trasck.integration;

import java.util.List;
import java.util.UUID;

public record ImportJobVersionDiffResponse(
        UUID importJobId,
        UUID workspaceId,
        Integer recordCount,
        Integer versionCount,
        Integer diffCount,
        List<ImportJobRecordVersionDiffGroupResponse> records
) {
}
