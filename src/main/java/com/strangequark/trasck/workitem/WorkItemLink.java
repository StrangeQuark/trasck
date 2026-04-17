package com.strangequark.trasck.workitem;

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

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "work_item_links")
public class WorkItemLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "source_work_item_id")
    private UUID sourceWorkItemId;

    @Column(name = "target_work_item_id")
    private UUID targetWorkItemId;

    @Column(name = "link_type")
    private String linkType;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourceWorkItemId() {
        return sourceWorkItemId;
    }

    public void setSourceWorkItemId(UUID sourceWorkItemId) {
        this.sourceWorkItemId = sourceWorkItemId;
    }

    public UUID getTargetWorkItemId() {
        return targetWorkItemId;
    }

    public void setTargetWorkItemId(UUID targetWorkItemId) {
        this.targetWorkItemId = targetWorkItemId;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public UUID getCreatedById() {
        return createdById;
    }

    public void setCreatedById(UUID createdById) {
        this.createdById = createdById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
