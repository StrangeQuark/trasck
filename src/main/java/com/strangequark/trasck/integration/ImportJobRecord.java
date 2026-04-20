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
@Table(name = "import_job_records")
public class ImportJobRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "status")
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private JsonNode rawPayload;

    @Column(name = "conflict_status")
    private String conflictStatus;

    @Column(name = "conflict_reason")
    private String conflictReason;

    @Column(name = "conflict_detected_at")
    private OffsetDateTime conflictDetectedAt;

    @Column(name = "conflict_resolved_at")
    private OffsetDateTime conflictResolvedAt;

    @Column(name = "conflict_resolution")
    private String conflictResolution;

    @Column(name = "conflict_resolved_by_id")
    private UUID conflictResolvedById;

    @Column(name = "conflict_materialization_run_id")
    private UUID conflictMaterializationRunId;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(UUID importJobId) {
        this.importJobId = importJobId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
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

    public JsonNode getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(JsonNode rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getConflictStatus() {
        return conflictStatus;
    }

    public void setConflictStatus(String conflictStatus) {
        this.conflictStatus = conflictStatus;
    }

    public String getConflictReason() {
        return conflictReason;
    }

    public void setConflictReason(String conflictReason) {
        this.conflictReason = conflictReason;
    }

    public OffsetDateTime getConflictDetectedAt() {
        return conflictDetectedAt;
    }

    public void setConflictDetectedAt(OffsetDateTime conflictDetectedAt) {
        this.conflictDetectedAt = conflictDetectedAt;
    }

    public OffsetDateTime getConflictResolvedAt() {
        return conflictResolvedAt;
    }

    public void setConflictResolvedAt(OffsetDateTime conflictResolvedAt) {
        this.conflictResolvedAt = conflictResolvedAt;
    }

    public String getConflictResolution() {
        return conflictResolution;
    }

    public void setConflictResolution(String conflictResolution) {
        this.conflictResolution = conflictResolution;
    }

    public UUID getConflictResolvedById() {
        return conflictResolvedById;
    }

    public void setConflictResolvedById(UUID conflictResolvedById) {
        this.conflictResolvedById = conflictResolvedById;
    }

    public UUID getConflictMaterializationRunId() {
        return conflictMaterializationRunId;
    }

    public void setConflictMaterializationRunId(UUID conflictMaterializationRunId) {
        this.conflictMaterializationRunId = conflictMaterializationRunId;
    }
}
