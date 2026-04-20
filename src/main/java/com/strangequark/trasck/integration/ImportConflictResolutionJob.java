package com.strangequark.trasck.integration;

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
@Table(name = "import_conflict_resolution_jobs")
public class ImportConflictResolutionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "scope")
    private String scope;

    @Column(name = "status")
    private String status;

    @Column(name = "status_filter")
    private String statusFilter;

    @Column(name = "conflict_status_filter")
    private String conflictStatusFilter;

    @Column(name = "source_type_filter")
    private String sourceTypeFilter;

    @Column(name = "expected_count")
    private Integer expectedCount;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "resolved_count")
    private Integer resolvedCount;

    @Column(name = "failed_count")
    private Integer failedCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "confirmation")
    private String confirmation;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

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

    public UUID getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(UUID importJobId) {
        this.importJobId = importJobId;
    }

    public UUID getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(UUID requestedById) {
        this.requestedById = requestedById;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusFilter() {
        return statusFilter;
    }

    public void setStatusFilter(String statusFilter) {
        this.statusFilter = statusFilter;
    }

    public String getConflictStatusFilter() {
        return conflictStatusFilter;
    }

    public void setConflictStatusFilter(String conflictStatusFilter) {
        this.conflictStatusFilter = conflictStatusFilter;
    }

    public String getSourceTypeFilter() {
        return sourceTypeFilter;
    }

    public void setSourceTypeFilter(String sourceTypeFilter) {
        this.sourceTypeFilter = sourceTypeFilter;
    }

    public Integer getExpectedCount() {
        return expectedCount;
    }

    public void setExpectedCount(Integer expectedCount) {
        this.expectedCount = expectedCount;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    public Integer getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(Integer resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(String confirmation) {
        this.confirmation = confirmation;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
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
}
