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
@Table(name = "reporting_cumulative_flow_rollups")
public class ReportingCumulativeFlowRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "board_id")
    private UUID boardId;
    @Column(name = "status_id")
    private UUID statusId;
    @Column(name = "granularity")
    private String granularity;
    @Column(name = "bucket_start_date")
    private LocalDate bucketStartDate;
    @Column(name = "bucket_end_date")
    private LocalDate bucketEndDate;
    @Column(name = "snapshot_count")
    private Integer snapshotCount;
    @Column(name = "work_item_count_avg")
    private BigDecimal workItemCountAvg;
    @Column(name = "work_item_count_max")
    private Integer workItemCountMax;
    @Column(name = "total_points_avg")
    private BigDecimal totalPointsAvg;
    @Column(name = "total_points_max")
    private BigDecimal totalPointsMax;
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
    public UUID getBoardId() { return boardId; }
    public void setBoardId(UUID boardId) { this.boardId = boardId; }
    public UUID getStatusId() { return statusId; }
    public void setStatusId(UUID statusId) { this.statusId = statusId; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public LocalDate getBucketStartDate() { return bucketStartDate; }
    public void setBucketStartDate(LocalDate bucketStartDate) { this.bucketStartDate = bucketStartDate; }
    public LocalDate getBucketEndDate() { return bucketEndDate; }
    public void setBucketEndDate(LocalDate bucketEndDate) { this.bucketEndDate = bucketEndDate; }
    public Integer getSnapshotCount() { return snapshotCount; }
    public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }
    public BigDecimal getWorkItemCountAvg() { return workItemCountAvg; }
    public void setWorkItemCountAvg(BigDecimal workItemCountAvg) { this.workItemCountAvg = workItemCountAvg; }
    public Integer getWorkItemCountMax() { return workItemCountMax; }
    public void setWorkItemCountMax(Integer workItemCountMax) { this.workItemCountMax = workItemCountMax; }
    public BigDecimal getTotalPointsAvg() { return totalPointsAvg; }
    public void setTotalPointsAvg(BigDecimal totalPointsAvg) { this.totalPointsAvg = totalPointsAvg; }
    public BigDecimal getTotalPointsMax() { return totalPointsMax; }
    public void setTotalPointsMax(BigDecimal totalPointsMax) { this.totalPointsMax = totalPointsMax; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
