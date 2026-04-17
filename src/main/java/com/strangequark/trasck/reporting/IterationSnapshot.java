package com.strangequark.trasck.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "iteration_snapshots")
public class IterationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "iteration_id")
    private UUID iterationId;

    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

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


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIterationId() {
        return iterationId;
    }

    public void setIterationId(UUID iterationId) {
        this.iterationId = iterationId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public BigDecimal getCommittedPoints() {
        return committedPoints;
    }

    public void setCommittedPoints(BigDecimal committedPoints) {
        this.committedPoints = committedPoints;
    }

    public BigDecimal getCompletedPoints() {
        return completedPoints;
    }

    public void setCompletedPoints(BigDecimal completedPoints) {
        this.completedPoints = completedPoints;
    }

    public BigDecimal getRemainingPoints() {
        return remainingPoints;
    }

    public void setRemainingPoints(BigDecimal remainingPoints) {
        this.remainingPoints = remainingPoints;
    }

    public BigDecimal getScopeAddedPoints() {
        return scopeAddedPoints;
    }

    public void setScopeAddedPoints(BigDecimal scopeAddedPoints) {
        this.scopeAddedPoints = scopeAddedPoints;
    }

    public BigDecimal getScopeRemovedPoints() {
        return scopeRemovedPoints;
    }

    public void setScopeRemovedPoints(BigDecimal scopeRemovedPoints) {
        this.scopeRemovedPoints = scopeRemovedPoints;
    }
}
