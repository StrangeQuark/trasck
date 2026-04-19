package com.strangequark.trasck.reporting;

public record ReportingRetentionPolicyRequest(
        Integer rawRetentionDays,
        Integer weeklyRollupAfterDays,
        Integer monthlyRollupAfterDays,
        Integer archiveAfterDays,
        Boolean destructivePruningEnabled
) {
}
