package com.strangequark.trasck.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WorkItemAttachmentId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    public WorkItemAttachmentId() {
    }

    public WorkItemAttachmentId(UUID workItemId, UUID attachmentId) {
        this.workItemId = workItemId;
        this.attachmentId = attachmentId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(UUID attachmentId) {
        this.attachmentId = attachmentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkItemAttachmentId that)) {
            return false;
        }
        return Objects.equals(workItemId, that.workItemId) && Objects.equals(attachmentId, that.attachmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workItemId, attachmentId);
    }
}
