package com.strangequark.trasck.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AuditRetentionExportResponse(
        UUID workspaceId,
        boolean retentionEnabled,
        Integer retentionDays,
        OffsetDateTime cutoff,
        long entriesEligible,
        List<AuditLogEntryResponse> entries
) {
}
