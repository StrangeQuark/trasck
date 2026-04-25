package com.strangequark.trasck.project;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectManagementService projectManagementService;

    public ProjectController(ProjectManagementService projectManagementService) {
        this.projectManagementService = projectManagementService;
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public List<ProjectResponse> listWorkspaceProjects(@PathVariable UUID workspaceId) {
        return projectManagementService.listWorkspaceProjects(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    public ResponseEntity<ProjectResponse> createProject(
            @PathVariable UUID workspaceId,
            @RequestBody ProjectRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectManagementService.createProject(workspaceId, request));
    }

    @GetMapping("/projects/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId) {
        return projectManagementService.getProject(projectId);
    }

    @PatchMapping("/projects/{projectId}")
    public ProjectResponse updateProject(
            @PathVariable UUID projectId,
            @RequestBody ProjectRequest request
    ) {
        return projectManagementService.updateProject(projectId, request);
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> archiveProject(@PathVariable UUID projectId) {
        projectManagementService.archiveProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
