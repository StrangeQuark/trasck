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
@Table(name = "reporting_iteration_rollups")
public class ReportingIterationRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "team_id")
    private UUID teamId;
    @Column(name = "team_scope_key")
    private UUID teamScopeKey;
    @Column(name = "granularity")
    private String granularity;
    @Column(name = "bucket_start_date")
    private LocalDate bucketStartDate;
    @Column(name = "bucket_end_date")
    private LocalDate bucketEndDate;
    @Column(name = "iteration_count")
    private Integer iterationCount;
    @Column(name = "committed_points")
    private BigDecimal committedPoints;
    @Column(name = "completed_points")
    private BigDecimal completedPoints;
    @Column(name = "remaining_points")
    private BigDecimal remainingPoints;
    @Column(name = "scope_added_points")
    private BigDecimal scopeAddedPoints;
    @Column(name = "scope_removed_points")
    private BigDecimal scopeRemovedPoints;
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
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public UUID getTeamScopeKey() { return teamScopeKey; }
    public void setTeamScopeKey(UUID teamScopeKey) { this.teamScopeKey = teamScopeKey; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public LocalDate getBucketStartDate() { return bucketStartDate; }
    public void setBucketStartDate(LocalDate bucketStartDate) { this.bucketStartDate = bucketStartDate; }
    public LocalDate getBucketEndDate() { return bucketEndDate; }
    public void setBucketEndDate(LocalDate bucketEndDate) { this.bucketEndDate = bucketEndDate; }
    public Integer getIterationCount() { return iterationCount; }
    public void setIterationCount(Integer iterationCount) { this.iterationCount = iterationCount; }
    public BigDecimal getCommittedPoints() { return committedPoints; }
    public void setCommittedPoints(BigDecimal committedPoints) { this.committedPoints = committedPoints; }
    public BigDecimal getCompletedPoints() { return completedPoints; }
    public void setCompletedPoints(BigDecimal completedPoints) { this.completedPoints = completedPoints; }
    public BigDecimal getRemainingPoints() { return remainingPoints; }
    public void setRemainingPoints(BigDecimal remainingPoints) { this.remainingPoints = remainingPoints; }
    public BigDecimal getScopeAddedPoints() { return scopeAddedPoints; }
    public void setScopeAddedPoints(BigDecimal scopeAddedPoints) { this.scopeAddedPoints = scopeAddedPoints; }
    public BigDecimal getScopeRemovedPoints() { return scopeRemovedPoints; }
    public void setScopeRemovedPoints(BigDecimal scopeRemovedPoints) { this.scopeRemovedPoints = scopeRemovedPoints; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
