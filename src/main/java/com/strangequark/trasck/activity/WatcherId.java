package com.strangequark.trasck.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WatcherId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "user_id")
    private UUID userId;

    public WatcherId() {
    }

    public WatcherId(UUID workItemId, UUID userId) {
        this.workItemId = workItemId;
        this.userId = userId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WatcherId that)) {
            return false;
        }
        return Objects.equals(workItemId, that.workItemId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workItemId, userId);
    }
}
