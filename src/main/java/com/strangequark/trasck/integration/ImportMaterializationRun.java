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
@Table(name = "import_materialization_runs")
public class ImportMaterializationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "mapping_template_id")
    private UUID mappingTemplateId;

    @Column(name = "transform_preset_id")
    private UUID transformPresetId;

    @Column(name = "transform_preset_version")
    private Integer transformPresetVersion;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "update_existing")
    private Boolean updateExisting;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_template_snapshot")
    private JsonNode mappingTemplateSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transform_preset_snapshot")
    private JsonNode transformPresetSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transformation_config_snapshot")
    private JsonNode transformationConfigSnapshot;

    @Column(name = "status")
    private String status;

    @Column(name = "records_processed")
    private Integer recordsProcessed;

    @Column(name = "records_created")
    private Integer recordsCreated;

    @Column(name = "records_updated")
    private Integer recordsUpdated;

    @Column(name = "records_failed")
    private Integer recordsFailed;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public UUID getId() {
        return id;
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

    public UUID getMappingTemplateId() {
        return mappingTemplateId;
    }

    public void setMappingTemplateId(UUID mappingTemplateId) {
        this.mappingTemplateId = mappingTemplateId;
    }

    public UUID getTransformPresetId() {
        return transformPresetId;
    }

    public void setTransformPresetId(UUID transformPresetId) {
        this.transformPresetId = transformPresetId;
    }

    public Integer getTransformPresetVersion() {
        return transformPresetVersion;
    }

    public void setTransformPresetVersion(Integer transformPresetVersion) {
        this.transformPresetVersion = transformPresetVersion;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(UUID requestedById) {
        this.requestedById = requestedById;
    }

    public Boolean getUpdateExisting() {
        return updateExisting;
    }

    public void setUpdateExisting(Boolean updateExisting) {
        this.updateExisting = updateExisting;
    }

    public JsonNode getMappingTemplateSnapshot() {
        return mappingTemplateSnapshot;
    }

    public void setMappingTemplateSnapshot(JsonNode mappingTemplateSnapshot) {
        this.mappingTemplateSnapshot = mappingTemplateSnapshot;
    }

    public JsonNode getTransformPresetSnapshot() {
        return transformPresetSnapshot;
    }

    public void setTransformPresetSnapshot(JsonNode transformPresetSnapshot) {
        this.transformPresetSnapshot = transformPresetSnapshot;
    }

    public JsonNode getTransformationConfigSnapshot() {
        return transformationConfigSnapshot;
    }

    public void setTransformationConfigSnapshot(JsonNode transformationConfigSnapshot) {
        this.transformationConfigSnapshot = transformationConfigSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRecordsProcessed() {
        return recordsProcessed;
    }

    public void setRecordsProcessed(Integer recordsProcessed) {
        this.recordsProcessed = recordsProcessed;
    }

    public Integer getRecordsCreated() {
        return recordsCreated;
    }

    public void setRecordsCreated(Integer recordsCreated) {
        this.recordsCreated = recordsCreated;
    }

    public Integer getRecordsUpdated() {
        return recordsUpdated;
    }

    public void setRecordsUpdated(Integer recordsUpdated) {
        this.recordsUpdated = recordsUpdated;
    }

    public Integer getRecordsFailed() {
        return recordsFailed;
    }

    public void setRecordsFailed(Integer recordsFailed) {
        this.recordsFailed = recordsFailed;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
