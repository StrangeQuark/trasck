package com.strangequark.trasck.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramProjectRepository extends JpaRepository<ProgramProject, ProgramProjectId> {
    List<ProgramProject> findByIdProgramIdOrderByPositionAscCreatedAtAsc(UUID programId);

    boolean existsByIdProgramIdAndIdProjectId(UUID programId, UUID projectId);

    void deleteByIdProgramIdAndIdProjectId(UUID programId, UUID projectId);
}
