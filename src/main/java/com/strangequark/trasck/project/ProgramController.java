package com.strangequark.trasck.project;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping("/workspaces/{workspaceId}/programs")
    public List<ProgramResponse> listPrograms(@PathVariable UUID workspaceId) {
        return programService.listPrograms(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/programs")
    public ResponseEntity<ProgramResponse> createProgram(
            @PathVariable UUID workspaceId,
            @RequestBody ProgramRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programService.createProgram(workspaceId, request));
    }

    @GetMapping("/programs/{programId}")
    public ProgramResponse getProgram(@PathVariable UUID programId) {
        return programService.getProgram(programId);
    }

    @PatchMapping("/programs/{programId}")
    public ProgramResponse updateProgram(
            @PathVariable UUID programId,
            @RequestBody ProgramRequest request
    ) {
        return programService.updateProgram(programId, request);
    }

    @DeleteMapping("/programs/{programId}")
    public ResponseEntity<Void> archiveProgram(@PathVariable UUID programId) {
        programService.archiveProgram(programId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/programs/{programId}/projects")
    public List<ProgramProjectResponse> listProgramProjects(@PathVariable UUID programId) {
        return programService.listProgramProjects(programId);
    }

    @PutMapping("/programs/{programId}/projects/{projectId}")
    public ProgramProjectResponse assignProgramProject(
            @PathVariable UUID programId,
            @PathVariable UUID projectId,
            @RequestBody(required = false) ProgramProjectRequest request
    ) {
        return programService.assignProgramProject(programId, projectId, request);
    }

    @DeleteMapping("/programs/{programId}/projects/{projectId}")
    public ResponseEntity<Void> removeProgramProject(
            @PathVariable UUID programId,
            @PathVariable UUID projectId
    ) {
        programService.removeProgramProject(programId, projectId);
        return ResponseEntity.noContent().build();
    }
}
