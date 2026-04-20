package com.strangequark.trasck.automation;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AutomationWorkerSettingsResponse(
        UUID workspaceId,
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
        Boolean workerRunExportBeforePrune,
        Boolean workerRunPruningAutomaticEnabled,
        Integer workerRunPruningIntervalMinutes,
        LocalTime workerRunPruningWindowStart,
        LocalTime workerRunPruningWindowEnd,
        OffsetDateTime workerRunPruningLastStartedAt,
        OffsetDateTime workerRunPruningLastFinishedAt,
        OffsetDateTime updatedAt
) {
    static AutomationWorkerSettingsResponse from(AutomationWorkerSettings settings) {
        return new AutomationWorkerSettingsResponse(
                settings.getWorkspaceId(),
                settings.getAutomationJobsEnabled(),
                settings.getWebhookDeliveriesEnabled(),
                settings.getEmailDeliveriesEnabled(),
                settings.getAutomationLimit(),
                settings.getWebhookLimit(),
                settings.getEmailLimit(),
                settings.getWebhookMaxAttempts(),
                settings.getEmailMaxAttempts(),
                settings.getWebhookDryRun(),
                settings.getEmailDryRun(),
                settings.getWorkerRunRetentionEnabled(),
                settings.getWorkerRunRetentionDays(),
                settings.getWorkerRunExportBeforePrune(),
                settings.getWorkerRunPruningAutomaticEnabled(),
                settings.getWorkerRunPruningIntervalMinutes(),
                settings.getWorkerRunPruningWindowStart(),
                settings.getWorkerRunPruningWindowEnd(),
                settings.getWorkerRunPruningLastStartedAt(),
                settings.getWorkerRunPruningLastFinishedAt(),
                settings.getUpdatedAt()
        );
    }
}
