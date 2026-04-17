package com.strangequark.trasck.workitem;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WorkItemLabelId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "label_id")
    private UUID labelId;

    public WorkItemLabelId() {
    }

    public WorkItemLabelId(UUID workItemId, UUID labelId) {
        this.workItemId = workItemId;
        this.labelId = labelId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getLabelId() {
        return labelId;
    }

    public void setLabelId(UUID labelId) {
        this.labelId = labelId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkItemLabelId that)) {
            return false;
        }
        return Objects.equals(workItemId, that.workItemId) && Objects.equals(labelId, that.labelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workItemId, labelId);
    }
}
