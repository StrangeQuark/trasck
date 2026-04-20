package com.strangequark.trasck.automation;

public record AutomationWorkerSettingsRequest(
        Boolean automationJobsEnabled,
        Boolean webhookDeliveriesEnabled,
        Boolean emailDeliveriesEnabled,
        Integer automationLimit,
        Integer webhookLimit,
        Integer emailLimit,
        Integer webhookMaxAttempts,
        Integer emailMaxAttempts,
        Boolean webhookDryRun,
        Boolean emailDryRun,
        Boolean workerRunRetentionEnabled,
        Integer workerRunRetentionDays,
        Boolean workerRunExportBeforePrune
) {
}
