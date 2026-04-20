package com.strangequark.trasck.automation;

import java.time.LocalTime;

public record AutomationWorkerSettingsRequest(
        Boolean automationJobsEnabled,
        Boolean webhookDeliveriesEnabled,
        Boolean emailDeliveriesEnabled,
        Boolean importConflictResolutionEnabled,
        Boolean importReviewExportsEnabled,
        Integer automationLimit,
        Integer webhookLimit,
        Integer emailLimit,
        Integer importConflictResolutionLimit,
        Integer importReviewExportLimit,
        Integer webhookMaxAttempts,
        Integer emailMaxAttempts,
        Boolean webhookDryRun,
        Boolean emailDryRun,
        Boolean workerRunRetentionEnabled,
        Integer workerRunRetentionDays,
        Boolean workerRunExportBeforePrune,
        Boolean workerRunPruningAutomaticEnabled,
        Integer workerRunPruningIntervalMinutes,
        LocalTime workerRunPruningWindowStart,
        LocalTime workerRunPruningWindowEnd,
        Boolean agentDispatchAttemptRetentionEnabled,
        Integer agentDispatchAttemptRetentionDays,
        Boolean agentDispatchAttemptExportBeforePrune,
        Boolean agentDispatchAttemptPruningAutomaticEnabled,
        Integer agentDispatchAttemptPruningIntervalMinutes,
        LocalTime agentDispatchAttemptPruningWindowStart,
        LocalTime agentDispatchAttemptPruningWindowEnd
) {
}
