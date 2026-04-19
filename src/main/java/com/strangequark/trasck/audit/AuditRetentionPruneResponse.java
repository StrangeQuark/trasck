package com.strangequark.trasck.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditRetentionPruneResponse(
        UUID workspaceId,
        boolean retentionEnabled,
        Integer retentionDays,
        OffsetDateTime cutoff,
        long entriesEligible,
        long entriesPruned
) {
}
