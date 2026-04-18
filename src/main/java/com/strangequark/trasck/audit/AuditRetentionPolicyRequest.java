package com.strangequark.trasck.audit;

public record AuditRetentionPolicyRequest(
        Boolean retentionEnabled,
        Integer retentionDays
) {
}
