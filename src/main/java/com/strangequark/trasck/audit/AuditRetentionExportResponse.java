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
        UUID exportJobId,
        UUID fileAttachmentId,
        String filename,
        String storageKey,
        String checksum,
        long sizeBytes,
        List<AuditLogEntryResponse> entries
) {
}
