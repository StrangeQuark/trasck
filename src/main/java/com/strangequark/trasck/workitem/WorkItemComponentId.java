package com.strangequark.trasck.workitem;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WorkItemComponentId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "component_id")
    private UUID componentId;

    public WorkItemComponentId() {
    }

    public WorkItemComponentId(UUID workItemId, UUID componentId) {
        this.workItemId = workItemId;
        this.componentId = componentId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getComponentId() {
        return componentId;
    }

    public void setComponentId(UUID componentId) {
        this.componentId = componentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkItemComponentId that)) {
            return false;
        }
        return Objects.equals(workItemId, that.workItemId) && Objects.equals(componentId, that.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workItemId, componentId);
    }
}
