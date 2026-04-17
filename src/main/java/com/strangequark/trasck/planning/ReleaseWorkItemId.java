package com.strangequark.trasck.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReleaseWorkItemId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "release_id")
    private UUID releaseId;

    @Column(name = "work_item_id")
    private UUID workItemId;

    public ReleaseWorkItemId() {
    }

    public ReleaseWorkItemId(UUID releaseId, UUID workItemId) {
        this.releaseId = releaseId;
        this.workItemId = workItemId;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(UUID releaseId) {
        this.releaseId = releaseId;
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
        if (!(o instanceof ReleaseWorkItemId that)) {
            return false;
        }
        return Objects.equals(releaseId, that.releaseId) && Objects.equals(workItemId, that.workItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseId, workItemId);
    }
}
