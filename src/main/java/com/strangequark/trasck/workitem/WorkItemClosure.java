package com.strangequark.trasck.workitem;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "work_item_closure")
public class WorkItemClosure {

    @EmbeddedId
    private WorkItemClosureId id = new WorkItemClosureId();

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public WorkItemClosureId getId() {
        return id;
    }

    public void setId(WorkItemClosureId id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
