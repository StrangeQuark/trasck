package com.strangequark.trasck.activity;

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
@Table(name = "work_item_attachments")
public class WorkItemAttachment {

    @EmbeddedId
    private WorkItemAttachmentId id = new WorkItemAttachmentId();

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public WorkItemAttachmentId getId() {
        return id;
    }

    public void setId(WorkItemAttachmentId id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
