package com.strangequark.trasck.reporting;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportingRetentionPolicyResponse(
        UUID id,
        UUID workspaceId,
        int rawRetentionDays,
        int weeklyRollupAfterDays,
        int monthlyRollupAfterDays,
        int archiveAfterDays,
        boolean destructivePruningEnabled,
        UUID createdById,
        UUID updatedById,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ReportingRetentionPolicyResponse from(ReportingRetentionPolicy policy) {
        return new ReportingRetentionPolicyResponse(
                policy.getId(),
                policy.getWorkspaceId(),
                valueOrDefault(policy.getRawRetentionDays(), ReportingSnapshotService.DEFAULT_RAW_RETENTION_DAYS),
                valueOrDefault(policy.getWeeklyRollupAfterDays(), ReportingSnapshotService.DEFAULT_WEEKLY_ROLLUP_AFTER_DAYS),
                valueOrDefault(policy.getMonthlyRollupAfterDays(), ReportingSnapshotService.DEFAULT_MONTHLY_ROLLUP_AFTER_DAYS),
                valueOrDefault(policy.getArchiveAfterDays(), ReportingSnapshotService.DEFAULT_ARCHIVE_AFTER_DAYS),
                Boolean.TRUE.equals(policy.getDestructivePruningEnabled()),
                policy.getCreatedById(),
                policy.getUpdatedById(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }

    static ReportingRetentionPolicyResponse defaults(UUID workspaceId) {
        return new ReportingRetentionPolicyResponse(
                null,
                workspaceId,
                ReportingSnapshotService.DEFAULT_RAW_RETENTION_DAYS,
                ReportingSnapshotService.DEFAULT_WEEKLY_ROLLUP_AFTER_DAYS,
                ReportingSnapshotService.DEFAULT_MONTHLY_ROLLUP_AFTER_DAYS,
                ReportingSnapshotService.DEFAULT_ARCHIVE_AFTER_DAYS,
                false,
                null,
                null,
                null,
                null
        );
    }

    private static int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
