package com.strangequark.trasck.customfield;

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
@Table(name = "screen_assignments")
public class ScreenAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "screen_id")
    private UUID screenId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "work_item_type_id")
    private UUID workItemTypeId;

    @Column(name = "operation")
    private String operation;

    @Column(name = "priority")
    private Integer priority;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScreenId() {
        return screenId;
    }

    public void setScreenId(UUID screenId) {
        this.screenId = screenId;
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

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
