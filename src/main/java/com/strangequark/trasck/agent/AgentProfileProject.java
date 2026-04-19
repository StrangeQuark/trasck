package com.strangequark.trasck.agent;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "agent_profile_projects")
public class AgentProfileProject {

    @EmbeddedId
    private AgentProfileProjectId id = new AgentProfileProjectId();

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public AgentProfileProjectId getId() {
        return id;
    }

    public void setId(AgentProfileProjectId id) {
        this.id = id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
