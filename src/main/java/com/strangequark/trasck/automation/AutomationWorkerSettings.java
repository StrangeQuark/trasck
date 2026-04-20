package com.strangequark.trasck.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "automation_worker_settings")
public class AutomationWorkerSettings {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "automation_jobs_enabled")
    private Boolean automationJobsEnabled;

    @Column(name = "webhook_deliveries_enabled")
    private Boolean webhookDeliveriesEnabled;

    @Column(name = "email_deliveries_enabled")
    private Boolean emailDeliveriesEnabled;

    @Column(name = "import_conflict_resolution_enabled")
    private Boolean importConflictResolutionEnabled;

    @Column(name = "import_review_exports_enabled")
    private Boolean importReviewExportsEnabled;

    @Column(name = "automation_limit")
    private Integer automationLimit;

    @Column(name = "webhook_limit")
    private Integer webhookLimit;

    @Column(name = "email_limit")
    private Integer emailLimit;

    @Column(name = "import_conflict_resolution_limit")
    private Integer importConflictResolutionLimit;

    @Column(name = "import_review_export_limit")
    private Integer importReviewExportLimit;

    @Column(name = "webhook_max_attempts")
    private Integer webhookMaxAttempts;

    @Column(name = "email_max_attempts")
    private Integer emailMaxAttempts;

    @Column(name = "webhook_dry_run")
    private Boolean webhookDryRun;

    @Column(name = "email_dry_run")
    private Boolean emailDryRun;

    @Column(name = "worker_run_retention_enabled")
    private Boolean workerRunRetentionEnabled;

    @Column(name = "worker_run_retention_days")
    private Integer workerRunRetentionDays;

    @Column(name = "worker_run_export_before_prune")
    private Boolean workerRunExportBeforePrune;

    @Column(name = "worker_run_pruning_automatic_enabled")
    private Boolean workerRunPruningAutomaticEnabled;

    @Column(name = "worker_run_pruning_interval_minutes")
    private Integer workerRunPruningIntervalMinutes;

    @Column(name = "worker_run_pruning_window_start")
    private LocalTime workerRunPruningWindowStart;

    @Column(name = "worker_run_pruning_window_end")
    private LocalTime workerRunPruningWindowEnd;

    @Column(name = "worker_run_pruning_last_started_at")
    private OffsetDateTime workerRunPruningLastStartedAt;

    @Column(name = "worker_run_pruning_last_finished_at")
    private OffsetDateTime workerRunPruningLastFinishedAt;

    @Column(name = "agent_dispatch_attempt_retention_enabled")
    private Boolean agentDispatchAttemptRetentionEnabled;

    @Column(name = "agent_dispatch_attempt_retention_days")
    private Integer agentDispatchAttemptRetentionDays;

    @Column(name = "agent_dispatch_attempt_export_before_prune")
    private Boolean agentDispatchAttemptExportBeforePrune;

    @Column(name = "agent_dispatch_attempt_pruning_automatic_enabled")
    private Boolean agentDispatchAttemptPruningAutomaticEnabled;

    @Column(name = "agent_dispatch_attempt_pruning_interval_minutes")
    private Integer agentDispatchAttemptPruningIntervalMinutes;

    @Column(name = "agent_dispatch_attempt_pruning_window_start")
    private LocalTime agentDispatchAttemptPruningWindowStart;

    @Column(name = "agent_dispatch_attempt_pruning_window_end")
    private LocalTime agentDispatchAttemptPruningWindowEnd;

    @Column(name = "agent_dispatch_attempt_pruning_last_started_at")
    private OffsetDateTime agentDispatchAttemptPruningLastStartedAt;

    @Column(name = "agent_dispatch_attempt_pruning_last_finished_at")
    private OffsetDateTime agentDispatchAttemptPruningLastFinishedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Boolean getAutomationJobsEnabled() {
        return automationJobsEnabled;
    }

    public void setAutomationJobsEnabled(Boolean automationJobsEnabled) {
        this.automationJobsEnabled = automationJobsEnabled;
    }

    public Boolean getWebhookDeliveriesEnabled() {
        return webhookDeliveriesEnabled;
    }

    public void setWebhookDeliveriesEnabled(Boolean webhookDeliveriesEnabled) {
        this.webhookDeliveriesEnabled = webhookDeliveriesEnabled;
    }

    public Boolean getEmailDeliveriesEnabled() {
        return emailDeliveriesEnabled;
    }

    public void setEmailDeliveriesEnabled(Boolean emailDeliveriesEnabled) {
        this.emailDeliveriesEnabled = emailDeliveriesEnabled;
    }

    public Boolean getImportConflictResolutionEnabled() {
        return importConflictResolutionEnabled;
    }

    public void setImportConflictResolutionEnabled(Boolean importConflictResolutionEnabled) {
        this.importConflictResolutionEnabled = importConflictResolutionEnabled;
    }

    public Boolean getImportReviewExportsEnabled() {
        return importReviewExportsEnabled;
    }

    public void setImportReviewExportsEnabled(Boolean importReviewExportsEnabled) {
        this.importReviewExportsEnabled = importReviewExportsEnabled;
    }

    public Integer getAutomationLimit() {
        return automationLimit;
    }

    public void setAutomationLimit(Integer automationLimit) {
        this.automationLimit = automationLimit;
    }

    public Integer getWebhookLimit() {
        return webhookLimit;
    }

    public void setWebhookLimit(Integer webhookLimit) {
        this.webhookLimit = webhookLimit;
    }

    public Integer getEmailLimit() {
        return emailLimit;
    }

    public void setEmailLimit(Integer emailLimit) {
        this.emailLimit = emailLimit;
    }

    public Integer getImportConflictResolutionLimit() {
        return importConflictResolutionLimit;
    }

    public void setImportConflictResolutionLimit(Integer importConflictResolutionLimit) {
        this.importConflictResolutionLimit = importConflictResolutionLimit;
    }

    public Integer getImportReviewExportLimit() {
        return importReviewExportLimit;
    }

    public void setImportReviewExportLimit(Integer importReviewExportLimit) {
        this.importReviewExportLimit = importReviewExportLimit;
    }

    public Integer getWebhookMaxAttempts() {
        return webhookMaxAttempts;
    }

    public void setWebhookMaxAttempts(Integer webhookMaxAttempts) {
        this.webhookMaxAttempts = webhookMaxAttempts;
    }

    public Integer getEmailMaxAttempts() {
        return emailMaxAttempts;
    }

    public void setEmailMaxAttempts(Integer emailMaxAttempts) {
        this.emailMaxAttempts = emailMaxAttempts;
    }

    public Boolean getWebhookDryRun() {
        return webhookDryRun;
    }

    public void setWebhookDryRun(Boolean webhookDryRun) {
        this.webhookDryRun = webhookDryRun;
    }

    public Boolean getEmailDryRun() {
        return emailDryRun;
    }

    public void setEmailDryRun(Boolean emailDryRun) {
        this.emailDryRun = emailDryRun;
    }

    public Boolean getWorkerRunRetentionEnabled() {
        return workerRunRetentionEnabled;
    }

    public void setWorkerRunRetentionEnabled(Boolean workerRunRetentionEnabled) {
        this.workerRunRetentionEnabled = workerRunRetentionEnabled;
    }

    public Integer getWorkerRunRetentionDays() {
        return workerRunRetentionDays;
    }

    public void setWorkerRunRetentionDays(Integer workerRunRetentionDays) {
        this.workerRunRetentionDays = workerRunRetentionDays;
    }

    public Boolean getWorkerRunExportBeforePrune() {
        return workerRunExportBeforePrune;
    }

    public void setWorkerRunExportBeforePrune(Boolean workerRunExportBeforePrune) {
        this.workerRunExportBeforePrune = workerRunExportBeforePrune;
    }

    public Boolean getWorkerRunPruningAutomaticEnabled() {
        return workerRunPruningAutomaticEnabled;
    }

    public void setWorkerRunPruningAutomaticEnabled(Boolean workerRunPruningAutomaticEnabled) {
        this.workerRunPruningAutomaticEnabled = workerRunPruningAutomaticEnabled;
    }

    public Integer getWorkerRunPruningIntervalMinutes() {
        return workerRunPruningIntervalMinutes;
    }

    public void setWorkerRunPruningIntervalMinutes(Integer workerRunPruningIntervalMinutes) {
        this.workerRunPruningIntervalMinutes = workerRunPruningIntervalMinutes;
    }

    public LocalTime getWorkerRunPruningWindowStart() {
        return workerRunPruningWindowStart;
    }

    public void setWorkerRunPruningWindowStart(LocalTime workerRunPruningWindowStart) {
        this.workerRunPruningWindowStart = workerRunPruningWindowStart;
    }

    public LocalTime getWorkerRunPruningWindowEnd() {
        return workerRunPruningWindowEnd;
    }

    public void setWorkerRunPruningWindowEnd(LocalTime workerRunPruningWindowEnd) {
        this.workerRunPruningWindowEnd = workerRunPruningWindowEnd;
    }

    public OffsetDateTime getWorkerRunPruningLastStartedAt() {
        return workerRunPruningLastStartedAt;
    }

    public void setWorkerRunPruningLastStartedAt(OffsetDateTime workerRunPruningLastStartedAt) {
        this.workerRunPruningLastStartedAt = workerRunPruningLastStartedAt;
    }

    public OffsetDateTime getWorkerRunPruningLastFinishedAt() {
        return workerRunPruningLastFinishedAt;
    }

    public void setWorkerRunPruningLastFinishedAt(OffsetDateTime workerRunPruningLastFinishedAt) {
        this.workerRunPruningLastFinishedAt = workerRunPruningLastFinishedAt;
    }

    public Boolean getAgentDispatchAttemptRetentionEnabled() {
        return agentDispatchAttemptRetentionEnabled;
    }

    public void setAgentDispatchAttemptRetentionEnabled(Boolean agentDispatchAttemptRetentionEnabled) {
        this.agentDispatchAttemptRetentionEnabled = agentDispatchAttemptRetentionEnabled;
    }

    public Integer getAgentDispatchAttemptRetentionDays() {
        return agentDispatchAttemptRetentionDays;
    }

    public void setAgentDispatchAttemptRetentionDays(Integer agentDispatchAttemptRetentionDays) {
        this.agentDispatchAttemptRetentionDays = agentDispatchAttemptRetentionDays;
    }

    public Boolean getAgentDispatchAttemptExportBeforePrune() {
        return agentDispatchAttemptExportBeforePrune;
    }

    public void setAgentDispatchAttemptExportBeforePrune(Boolean agentDispatchAttemptExportBeforePrune) {
        this.agentDispatchAttemptExportBeforePrune = agentDispatchAttemptExportBeforePrune;
    }

    public Boolean getAgentDispatchAttemptPruningAutomaticEnabled() {
        return agentDispatchAttemptPruningAutomaticEnabled;
    }

    public void setAgentDispatchAttemptPruningAutomaticEnabled(Boolean agentDispatchAttemptPruningAutomaticEnabled) {
        this.agentDispatchAttemptPruningAutomaticEnabled = agentDispatchAttemptPruningAutomaticEnabled;
    }

    public Integer getAgentDispatchAttemptPruningIntervalMinutes() {
        return agentDispatchAttemptPruningIntervalMinutes;
    }

    public void setAgentDispatchAttemptPruningIntervalMinutes(Integer agentDispatchAttemptPruningIntervalMinutes) {
        this.agentDispatchAttemptPruningIntervalMinutes = agentDispatchAttemptPruningIntervalMinutes;
    }

    public LocalTime getAgentDispatchAttemptPruningWindowStart() {
        return agentDispatchAttemptPruningWindowStart;
    }

    public void setAgentDispatchAttemptPruningWindowStart(LocalTime agentDispatchAttemptPruningWindowStart) {
        this.agentDispatchAttemptPruningWindowStart = agentDispatchAttemptPruningWindowStart;
    }

    public LocalTime getAgentDispatchAttemptPruningWindowEnd() {
        return agentDispatchAttemptPruningWindowEnd;
    }

    public void setAgentDispatchAttemptPruningWindowEnd(LocalTime agentDispatchAttemptPruningWindowEnd) {
        this.agentDispatchAttemptPruningWindowEnd = agentDispatchAttemptPruningWindowEnd;
    }

    public OffsetDateTime getAgentDispatchAttemptPruningLastStartedAt() {
        return agentDispatchAttemptPruningLastStartedAt;
    }

    public void setAgentDispatchAttemptPruningLastStartedAt(OffsetDateTime agentDispatchAttemptPruningLastStartedAt) {
        this.agentDispatchAttemptPruningLastStartedAt = agentDispatchAttemptPruningLastStartedAt;
    }

    public OffsetDateTime getAgentDispatchAttemptPruningLastFinishedAt() {
        return agentDispatchAttemptPruningLastFinishedAt;
    }

    public void setAgentDispatchAttemptPruningLastFinishedAt(OffsetDateTime agentDispatchAttemptPruningLastFinishedAt) {
        this.agentDispatchAttemptPruningLastFinishedAt = agentDispatchAttemptPruningLastFinishedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
