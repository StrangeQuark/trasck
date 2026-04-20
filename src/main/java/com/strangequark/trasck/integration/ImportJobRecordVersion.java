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
@Table(name = "import_job_record_versions")
public class ImportJobRecordVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "import_job_record_id")
    private UUID importJobRecordId;

    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "version")
    private Integer version;

    @Column(name = "change_type")
    private String changeType;

    @Column(name = "changed_by_id")
    private UUID changedById;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot")
    private JsonNode snapshot;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getImportJobRecordId() {
        return importJobRecordId;
    }

    public void setImportJobRecordId(UUID importJobRecordId) {
        this.importJobRecordId = importJobRecordId;
    }

    public UUID getImportJobId() {
        return importJobId;
    }

    public void setImportJobId(UUID importJobId) {
        this.importJobId = importJobId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public UUID getChangedById() {
        return changedById;
    }

    public void setChangedById(UUID changedById) {
        this.changedById = changedById;
    }

    public JsonNode getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(JsonNode snapshot) {
        this.snapshot = snapshot;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
