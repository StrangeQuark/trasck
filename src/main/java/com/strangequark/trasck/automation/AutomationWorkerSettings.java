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

    @Column(name = "automation_limit")
    private Integer automationLimit;

    @Column(name = "webhook_limit")
    private Integer webhookLimit;

    @Column(name = "email_limit")
    private Integer emailLimit;

    @Column(name = "import_conflict_resolution_limit")
    private Integer importConflictResolutionLimit;

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

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
