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
@Table(name = "workflow_statuses")
public class WorkflowStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "name")
    private String name;

    @Column(name = "key")
    private String key;

    @Column(name = "category")
    private String category;

    @Column(name = "color")
    private String color;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "terminal")
    private Boolean terminal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", insertable = false, updatable = false)
    private Workflow workflow;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getTerminal() {
        return terminal;
    }

    public void setTerminal(Boolean terminal) {
        this.terminal = terminal;
    }

    public Workflow getWorkflow() {
        return workflow;
    }
}
