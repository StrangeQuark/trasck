package com.strangequark.trasck.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "work_item_status_history")
public class WorkItemStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "from_status_id")
    private UUID fromStatusId;

    @Column(name = "to_status_id")
    private UUID toStatusId;

    @Column(name = "changed_by_id")
    private UUID changedById;

    @Column(name = "changed_at")
    private OffsetDateTime changedAt;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getFromStatusId() {
        return fromStatusId;
    }

    public void setFromStatusId(UUID fromStatusId) {
        this.fromStatusId = fromStatusId;
    }

    public UUID getToStatusId() {
        return toStatusId;
    }

    public void setToStatusId(UUID toStatusId) {
        this.toStatusId = toStatusId;
    }

    public UUID getChangedById() {
        return changedById;
    }

    public void setChangedById(UUID changedById) {
        this.changedById = changedById;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(OffsetDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
