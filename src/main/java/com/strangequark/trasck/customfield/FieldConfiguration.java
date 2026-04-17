package com.strangequark.trasck.customfield;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "field_configurations")
public class FieldConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "custom_field_id")
    private UUID customFieldId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "work_item_type_id")
    private UUID workItemTypeId;

    @Column(name = "required")
    private Boolean required;

    @Column(name = "hidden")
    private Boolean hidden;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_value")
    private JsonNode defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_config")
    private JsonNode validationConfig;


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

    public UUID getCustomFieldId() {
        return customFieldId;
    }

    public void setCustomFieldId(UUID customFieldId) {
        this.customFieldId = customFieldId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getWorkItemTypeId() {
        return workItemTypeId;
    }

    public void setWorkItemTypeId(UUID workItemTypeId) {
        this.workItemTypeId = workItemTypeId;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public JsonNode getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(JsonNode defaultValue) {
        this.defaultValue = defaultValue;
    }

    public JsonNode getValidationConfig() {
        return validationConfig;
    }

    public void setValidationConfig(JsonNode validationConfig) {
        this.validationConfig = validationConfig;
    }
}
