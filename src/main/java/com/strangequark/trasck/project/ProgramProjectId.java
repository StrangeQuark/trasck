package com.strangequark.trasck.project;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProgramProjectId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "program_id")
    private UUID programId;

    @Column(name = "project_id")
    private UUID projectId;

    public ProgramProjectId() {
    }

    public ProgramProjectId(UUID programId, UUID projectId) {
        this.programId = programId;
        this.projectId = projectId;
    }

    public UUID getProgramId() {
        return programId;
    }

    public void setProgramId(UUID programId) {
        this.programId = programId;
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
        if (!(o instanceof ProgramProjectId that)) {
            return false;
        }
        return Objects.equals(programId, that.programId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(programId, projectId);
    }
}
