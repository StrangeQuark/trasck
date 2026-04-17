package com.strangequark.trasck.workitem;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WorkItemClosureId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "ancestor_work_item_id")
    private UUID ancestorWorkItemId;

    @Column(name = "descendant_work_item_id")
    private UUID descendantWorkItemId;

    public WorkItemClosureId() {
    }

    public WorkItemClosureId(UUID ancestorWorkItemId, UUID descendantWorkItemId) {
        this.ancestorWorkItemId = ancestorWorkItemId;
        this.descendantWorkItemId = descendantWorkItemId;
    }

    public UUID getAncestorWorkItemId() {
        return ancestorWorkItemId;
    }

    public void setAncestorWorkItemId(UUID ancestorWorkItemId) {
        this.ancestorWorkItemId = ancestorWorkItemId;
    }

    public UUID getDescendantWorkItemId() {
        return descendantWorkItemId;
    }

    public void setDescendantWorkItemId(UUID descendantWorkItemId) {
        this.descendantWorkItemId = descendantWorkItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkItemClosureId that)) {
            return false;
        }
        return Objects.equals(ancestorWorkItemId, that.ancestorWorkItemId) && Objects.equals(descendantWorkItemId, that.descendantWorkItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ancestorWorkItemId, descendantWorkItemId);
    }
}
