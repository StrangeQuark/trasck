package com.strangequark.trasck.activity;

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
@Table(name = "watchers")
public class Watcher {

    @EmbeddedId
    private WatcherId id = new WatcherId();

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public WatcherId getId() {
        return id;
    }

    public void setId(WatcherId id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
