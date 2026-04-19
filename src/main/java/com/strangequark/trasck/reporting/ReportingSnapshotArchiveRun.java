package com.strangequark.trasck.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "reporting_snapshot_archive_runs")
public class ReportingSnapshotArchiveRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "retention_policy_id")
    private UUID retentionPolicyId;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "action")
    private String action;

    @Column(name = "granularity")
    private String granularity;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot")
    private JsonNode policySnapshot;

    @Column(name = "cycle_time_rollups")
    private Integer cycleTimeRollups;

    @Column(name = "iteration_rollups")
    private Integer iterationRollups;

    @Column(name = "velocity_rollups")
    private Integer velocityRollups;

    @Column(name = "cumulative_flow_rollups")
    private Integer cumulativeFlowRollups;

    @Column(name = "generic_rollups")
    private Integer genericRollups;

    @Column(name = "raw_rows_pruned")
    private Integer rawRowsPruned;

    @Column(name = "status")
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getRetentionPolicyId() {
        return retentionPolicyId;
    }

    public void setRetentionPolicyId(UUID retentionPolicyId) {
        this.retentionPolicyId = retentionPolicyId;
    }

    public UUID getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(UUID requestedById) {
        this.requestedById = requestedById;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getGranularity() {
        return granularity;
    }

    public void setGranularity(String granularity) {
        this.granularity = granularity;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public JsonNode getPolicySnapshot() {
        return policySnapshot;
    }

    public void setPolicySnapshot(JsonNode policySnapshot) {
        this.policySnapshot = policySnapshot;
    }

    public Integer getCycleTimeRollups() {
        return cycleTimeRollups;
    }

    public void setCycleTimeRollups(Integer cycleTimeRollups) {
        this.cycleTimeRollups = cycleTimeRollups;
    }

    public Integer getIterationRollups() {
        return iterationRollups;
    }

    public void setIterationRollups(Integer iterationRollups) {
        this.iterationRollups = iterationRollups;
    }

    public Integer getVelocityRollups() {
        return velocityRollups;
    }

    public void setVelocityRollups(Integer velocityRollups) {
        this.velocityRollups = velocityRollups;
    }

    public Integer getCumulativeFlowRollups() {
        return cumulativeFlowRollups;
    }

    public void setCumulativeFlowRollups(Integer cumulativeFlowRollups) {
        this.cumulativeFlowRollups = cumulativeFlowRollups;
    }

    public Integer getGenericRollups() {
        return genericRollups;
    }

    public void setGenericRollups(Integer genericRollups) {
        this.genericRollups = genericRollups;
    }

    public Integer getRawRowsPruned() {
        return rawRowsPruned;
    }

    public void setRawRowsPruned(Integer rawRowsPruned) {
        this.rawRowsPruned = rawRowsPruned;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
