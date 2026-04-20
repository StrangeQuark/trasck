package com.strangequark.trasck.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "provider")
    private String provider;

    @Column(name = "status")
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config")
    private JsonNode config;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "open_conflict_completion_accepted")
    private Boolean openConflictCompletionAccepted;

    @Column(name = "open_conflict_completion_count")
    private Integer openConflictCompletionCount;

    @Column(name = "open_conflict_completed_by_id")
    private UUID openConflictCompletedById;

    @Column(name = "open_conflict_completed_at")
    private OffsetDateTime openConflictCompletedAt;

    @Column(name = "open_conflict_completion_reason")
    private String openConflictCompletionReason;


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

    public UUID getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(UUID requestedById) {
        this.requestedById = requestedById;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode config) {
        this.config = config;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Boolean getOpenConflictCompletionAccepted() {
        return openConflictCompletionAccepted;
    }

    public void setOpenConflictCompletionAccepted(Boolean openConflictCompletionAccepted) {
        this.openConflictCompletionAccepted = openConflictCompletionAccepted;
    }

    public Integer getOpenConflictCompletionCount() {
        return openConflictCompletionCount;
    }

    public void setOpenConflictCompletionCount(Integer openConflictCompletionCount) {
        this.openConflictCompletionCount = openConflictCompletionCount;
    }

    public UUID getOpenConflictCompletedById() {
        return openConflictCompletedById;
    }

    public void setOpenConflictCompletedById(UUID openConflictCompletedById) {
        this.openConflictCompletedById = openConflictCompletedById;
    }

    public OffsetDateTime getOpenConflictCompletedAt() {
        return openConflictCompletedAt;
    }

    public void setOpenConflictCompletedAt(OffsetDateTime openConflictCompletedAt) {
        this.openConflictCompletedAt = openConflictCompletedAt;
    }

    public String getOpenConflictCompletionReason() {
        return openConflictCompletionReason;
    }

    public void setOpenConflictCompletionReason(String openConflictCompletionReason) {
        this.openConflictCompletionReason = openConflictCompletionReason;
    }
}
