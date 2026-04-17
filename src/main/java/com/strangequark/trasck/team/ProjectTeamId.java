package com.strangequark.trasck.team;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProjectTeamId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "team_id")
    private UUID teamId;

    public ProjectTeamId() {
    }

    public ProjectTeamId(UUID projectId, UUID teamId) {
        this.projectId = projectId;
        this.teamId = teamId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectTeamId that)) {
            return false;
        }
        return Objects.equals(projectId, that.projectId) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, teamId);
    }
}
