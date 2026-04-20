package com.strangequark.trasck.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@IdClass(AutomationWorkerHealthId.class)
@Table(name = "automation_worker_health")
public class AutomationWorkerHealth {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Id
    @Column(name = "worker_type")
    private String workerType;

    @Column(name = "last_run_id")
    private UUID lastRunId;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "last_started_at")
    private OffsetDateTime lastStartedAt;

    @Column(name = "last_finished_at")
    private OffsetDateTime lastFinishedAt;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getWorkerType() {
        return workerType;
    }

    public void setWorkerType(String workerType) {
        this.workerType = workerType;
    }

    public UUID getLastRunId() {
        return lastRunId;
    }

    public void setLastRunId(UUID lastRunId) {
        this.lastRunId = lastRunId;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public OffsetDateTime getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(OffsetDateTime lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public OffsetDateTime getLastFinishedAt() {
        return lastFinishedAt;
    }

    public void setLastFinishedAt(OffsetDateTime lastFinishedAt) {
        this.lastFinishedAt = lastFinishedAt;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
