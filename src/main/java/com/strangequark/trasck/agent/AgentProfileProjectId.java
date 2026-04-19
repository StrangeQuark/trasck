package com.strangequark.trasck.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AgentProfileProjectId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "agent_profile_id")
    private UUID agentProfileId;

    @Column(name = "project_id")
    private UUID projectId;

    public AgentProfileProjectId() {
    }

    public AgentProfileProjectId(UUID agentProfileId, UUID projectId) {
        this.agentProfileId = agentProfileId;
        this.projectId = projectId;
    }

    public UUID getAgentProfileId() {
        return agentProfileId;
    }

    public void setAgentProfileId(UUID agentProfileId) {
        this.agentProfileId = agentProfileId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentProfileProjectId that)) {
            return false;
        }
        return Objects.equals(agentProfileId, that.agentProfileId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentProfileId, projectId);
    }
}
