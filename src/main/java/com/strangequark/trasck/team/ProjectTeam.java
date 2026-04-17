package com.strangequark.trasck.team;

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
@Table(name = "project_teams")
public class ProjectTeam {

    @EmbeddedId
    private ProjectTeamId id = new ProjectTeamId();

    @Column(name = "role")
    private String role;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public ProjectTeamId getId() {
        return id;
    }

    public void setId(ProjectTeamId id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
