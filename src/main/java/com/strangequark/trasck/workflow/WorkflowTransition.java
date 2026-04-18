package com.strangequark.trasck.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "workflow_transitions")
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id")
    private UUID toStatusId;

    @Column(name = "name")
    private String name;

    @Column(name = "key")
    private String key;

    @Column(name = "global_transition")
    private Boolean globalTransition;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", insertable = false, updatable = false)
    private Workflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_status_id", insertable = false, updatable = false)
    private WorkflowStatus fromStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_status_id", insertable = false, updatable = false)
    private WorkflowStatus toStatus;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public UUID getFromStatusId() {
        return fromStatusId;
    }

    public void setFromStatusId(UUID fromStatusId) {
        this.fromStatusId = fromStatusId;
    }

    public UUID getToStatusId() {
        return toStatusId;
    }

    public void setToStatusId(UUID toStatusId) {
        this.toStatusId = toStatusId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Boolean getGlobalTransition() {
        return globalTransition;
    }

    public void setGlobalTransition(Boolean globalTransition) {
        this.globalTransition = globalTransition;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public WorkflowStatus getFromStatus() {
        return fromStatus;
    }

    public WorkflowStatus getToStatus() {
        return toStatus;
    }
}
