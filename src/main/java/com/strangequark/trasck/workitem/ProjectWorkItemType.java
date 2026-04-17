package com.strangequark.trasck.workitem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "project_work_item_types")
public class ProjectWorkItemType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "work_item_type_id")
    private UUID workItemTypeId;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "default_type")
    private Boolean defaultType;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getDefaultType() {
        return defaultType;
    }

    public void setDefaultType(Boolean defaultType) {
        this.defaultType = defaultType;
    }
}
