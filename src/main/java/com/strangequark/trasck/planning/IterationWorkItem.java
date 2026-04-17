package com.strangequark.trasck.planning;

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
@Table(name = "iteration_work_items")
public class IterationWorkItem {

    @EmbeddedId
    private IterationWorkItemId id = new IterationWorkItemId();

    @Column(name = "added_by_id")
    private UUID addedById;

    @Column(name = "added_at")
    private OffsetDateTime addedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    public IterationWorkItemId getId() {
        return id;
    }

    public void setId(IterationWorkItemId id) {
        this.id = id;
    }

    public UUID getAddedById() {
        return addedById;
    }

    public void setAddedById(UUID addedById) {
        this.addedById = addedById;
    }

    public OffsetDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(OffsetDateTime addedAt) {
        this.addedAt = addedAt;
    }

    public OffsetDateTime getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(OffsetDateTime removedAt) {
        this.removedAt = removedAt;
    }
}
