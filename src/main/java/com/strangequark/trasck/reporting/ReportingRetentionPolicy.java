package com.strangequark.trasck.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "reporting_retention_policies")
public class ReportingRetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "raw_retention_days")
    private Integer rawRetentionDays;

    @Column(name = "weekly_rollup_after_days")
    private Integer weeklyRollupAfterDays;

    @Column(name = "monthly_rollup_after_days")
    private Integer monthlyRollupAfterDays;

    @Column(name = "archive_after_days")
    private Integer archiveAfterDays;

    @Column(name = "destructive_pruning_enabled")
    private Boolean destructivePruningEnabled;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "updated_by_id")
    private UUID updatedById;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

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

    public Integer getRawRetentionDays() {
        return rawRetentionDays;
    }

    public void setRawRetentionDays(Integer rawRetentionDays) {
        this.rawRetentionDays = rawRetentionDays;
    }

    public Integer getWeeklyRollupAfterDays() {
        return weeklyRollupAfterDays;
    }

    public void setWeeklyRollupAfterDays(Integer weeklyRollupAfterDays) {
        this.weeklyRollupAfterDays = weeklyRollupAfterDays;
    }

    public Integer getMonthlyRollupAfterDays() {
        return monthlyRollupAfterDays;
    }

    public void setMonthlyRollupAfterDays(Integer monthlyRollupAfterDays) {
        this.monthlyRollupAfterDays = monthlyRollupAfterDays;
    }

    public Integer getArchiveAfterDays() {
        return archiveAfterDays;
    }

    public void setArchiveAfterDays(Integer archiveAfterDays) {
        this.archiveAfterDays = archiveAfterDays;
    }

    public Boolean getDestructivePruningEnabled() {
        return destructivePruningEnabled;
    }

    public void setDestructivePruningEnabled(Boolean destructivePruningEnabled) {
        this.destructivePruningEnabled = destructivePruningEnabled;
    }

    public UUID getCreatedById() {
        return createdById;
    }

    public void setCreatedById(UUID createdById) {
        this.createdById = createdById;
    }

    public UUID getUpdatedById() {
        return updatedById;
    }

    public void setUpdatedById(UUID updatedById) {
        this.updatedById = updatedById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
