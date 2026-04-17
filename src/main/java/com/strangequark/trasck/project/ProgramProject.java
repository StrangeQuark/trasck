package com.strangequark.trasck.project;

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
@Table(name = "program_projects")
public class ProgramProject {

    @EmbeddedId
    private ProgramProjectId id = new ProgramProjectId();

    @Column(name = "position")
    private Integer position;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public ProgramProjectId getId() {
        return id;
    }

    public void setId(ProgramProjectId id) {
        this.id = id;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
