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
@Table(name = "release_work_items")
public class ReleaseWorkItem {

    @EmbeddedId
    private ReleaseWorkItemId id = new ReleaseWorkItemId();

    @Column(name = "added_by_id")
    private UUID addedById;

    @Column(name = "added_at")
    private OffsetDateTime addedAt;

    public ReleaseWorkItemId getId() {
        return id;
    }

    public void setId(ReleaseWorkItemId id) {
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
}
