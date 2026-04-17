package com.strangequark.trasck.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IterationWorkItemId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "iteration_id")
    private UUID iterationId;

    @Column(name = "work_item_id")
    private UUID workItemId;

    public IterationWorkItemId() {
    }

    public IterationWorkItemId(UUID iterationId, UUID workItemId) {
        this.iterationId = iterationId;
        this.workItemId = workItemId;
    }

    public UUID getIterationId() {
        return iterationId;
    }

    public void setIterationId(UUID iterationId) {
        this.iterationId = iterationId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IterationWorkItemId that)) {
            return false;
        }
        return Objects.equals(iterationId, that.iterationId) && Objects.equals(workItemId, that.workItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iterationId, workItemId);
    }
}
