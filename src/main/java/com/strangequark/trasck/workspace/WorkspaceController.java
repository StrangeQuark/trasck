package com.strangequark.trasck.workspace;

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
public class WorkspaceController {

    private final WorkspaceManagementService workspaceManagementService;

    public WorkspaceController(WorkspaceManagementService workspaceManagementService) {
        this.workspaceManagementService = workspaceManagementService;
    }

    @GetMapping("/organizations/{organizationId}/workspaces")
    public List<WorkspaceResponse> listOrganizationWorkspaces(@PathVariable UUID organizationId) {
        return workspaceManagementService.listOrganizationWorkspaces(organizationId);
    }

    @PostMapping("/organizations/{organizationId}/workspaces")
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @PathVariable UUID organizationId,
            @RequestBody WorkspaceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workspaceManagementService.createWorkspace(organizationId, request));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable UUID workspaceId) {
        return workspaceManagementService.getWorkspace(workspaceId);
    }

    @PatchMapping("/workspaces/{workspaceId}")
    public WorkspaceResponse updateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestBody WorkspaceRequest request
    ) {
        return workspaceManagementService.updateWorkspace(workspaceId, request);
    }

    @DeleteMapping("/workspaces/{workspaceId}")
    public ResponseEntity<Void> archiveWorkspace(@PathVariable UUID workspaceId) {
        workspaceManagementService.archiveWorkspace(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
