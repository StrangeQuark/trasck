package com.strangequark.trasck.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "reporting_cycle_time_rollups")
public class ReportingCycleTimeRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "granularity")
    private String granularity;
    @Column(name = "bucket_start_date")
    private LocalDate bucketStartDate;
    @Column(name = "bucket_end_date")
    private LocalDate bucketEndDate;
    @Column(name = "work_item_count")
    private Integer workItemCount;
    @Column(name = "lead_time_minutes_sum")
    private Long leadTimeMinutesSum;
    @Column(name = "cycle_time_minutes_sum")
    private Long cycleTimeMinutesSum;
    @Column(name = "lead_time_minutes_avg")
    private BigDecimal leadTimeMinutesAvg;
    @Column(name = "cycle_time_minutes_avg")
    private BigDecimal cycleTimeMinutesAvg;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public LocalDate getBucketStartDate() { return bucketStartDate; }
    public void setBucketStartDate(LocalDate bucketStartDate) { this.bucketStartDate = bucketStartDate; }
    public LocalDate getBucketEndDate() { return bucketEndDate; }
    public void setBucketEndDate(LocalDate bucketEndDate) { this.bucketEndDate = bucketEndDate; }
    public Integer getWorkItemCount() { return workItemCount; }
    public void setWorkItemCount(Integer workItemCount) { this.workItemCount = workItemCount; }
    public Long getLeadTimeMinutesSum() { return leadTimeMinutesSum; }
    public void setLeadTimeMinutesSum(Long leadTimeMinutesSum) { this.leadTimeMinutesSum = leadTimeMinutesSum; }
    public Long getCycleTimeMinutesSum() { return cycleTimeMinutesSum; }
    public void setCycleTimeMinutesSum(Long cycleTimeMinutesSum) { this.cycleTimeMinutesSum = cycleTimeMinutesSum; }
    public BigDecimal getLeadTimeMinutesAvg() { return leadTimeMinutesAvg; }
    public void setLeadTimeMinutesAvg(BigDecimal leadTimeMinutesAvg) { this.leadTimeMinutesAvg = leadTimeMinutesAvg; }
    public BigDecimal getCycleTimeMinutesAvg() { return cycleTimeMinutesAvg; }
    public void setCycleTimeMinutesAvg(BigDecimal cycleTimeMinutesAvg) { this.cycleTimeMinutesAvg = cycleTimeMinutesAvg; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
