package com.strangequark.trasck.project;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "project_settings")
public class ProjectSettings {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "default_workflow_id")
    private UUID defaultWorkflowId;

    @Column(name = "default_board_id")
    private UUID defaultBoardId;

    @Column(name = "estimation_unit")
    private String estimationUnit;

    @Column(name = "allow_cross_project_parents")
    private Boolean allowCrossProjectParents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config")
    private JsonNode config;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getDefaultWorkflowId() {
        return defaultWorkflowId;
    }

    public void setDefaultWorkflowId(UUID defaultWorkflowId) {
        this.defaultWorkflowId = defaultWorkflowId;
    }

    public UUID getDefaultBoardId() {
        return defaultBoardId;
    }

    public void setDefaultBoardId(UUID defaultBoardId) {
        this.defaultBoardId = defaultBoardId;
    }

    public String getEstimationUnit() {
        return estimationUnit;
    }

    public void setEstimationUnit(String estimationUnit) {
        this.estimationUnit = estimationUnit;
    }

    public Boolean getAllowCrossProjectParents() {
        return allowCrossProjectParents;
    }

    public void setAllowCrossProjectParents(Boolean allowCrossProjectParents) {
        this.allowCrossProjectParents = allowCrossProjectParents;
    }

    public JsonNode getConfig() {
        return config;
    }

    public void setConfig(JsonNode config) {
        this.config = config;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
