package com.strangequark.trasck.workflow;

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
@Table(name = "workflow_assignments")
public class WorkflowAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "work_item_type_id")
    private UUID workItemTypeId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "default_for_project")
    private Boolean defaultForProject;


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

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public Boolean getDefaultForProject() {
        return defaultForProject;
    }

    public void setDefaultForProject(Boolean defaultForProject) {
        this.defaultForProject = defaultForProject;
    }
}
