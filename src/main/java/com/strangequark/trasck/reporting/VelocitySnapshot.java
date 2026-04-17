package com.strangequark.trasck.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "velocity_snapshots")
public class VelocitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "iteration_id")
    private UUID iterationId;

    @Column(name = "committed_points")
    private BigDecimal committedPoints;

    @Column(name = "completed_points")
    private BigDecimal completedPoints;

    @Column(name = "carried_over_points")
    private BigDecimal carriedOverPoints;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID getIterationId() {
        return iterationId;
    }

    public void setIterationId(UUID iterationId) {
        this.iterationId = iterationId;
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

    public BigDecimal getCarriedOverPoints() {
        return carriedOverPoints;
    }

    public void setCarriedOverPoints(BigDecimal carriedOverPoints) {
        this.carriedOverPoints = carriedOverPoints;
    }
}
