package com.strangequark.trasck.search;

import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.workitem.WorkItemResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SavedFilterController {

    private final SavedFilterService savedFilterService;
    private final SavedFilterExecutionService savedFilterExecutionService;

    public SavedFilterController(SavedFilterService savedFilterService, SavedFilterExecutionService savedFilterExecutionService) {
        this.savedFilterService = savedFilterService;
        this.savedFilterExecutionService = savedFilterExecutionService;
    }

    @GetMapping("/workspaces/{workspaceId}/saved-filters")
    public List<SavedFilterResponse> list(@PathVariable UUID workspaceId) {
        return savedFilterService.list(workspaceId);
    }

    @GetMapping("/projects/{projectId}/saved-filters")
    public List<SavedFilterResponse> listByProject(@PathVariable UUID projectId) {
        return savedFilterService.listByProject(projectId);
    }

    @GetMapping("/teams/{teamId}/saved-filters")
    public List<SavedFilterResponse> listByTeam(@PathVariable UUID teamId) {
        return savedFilterService.listByTeam(teamId);
    }

    @PostMapping("/workspaces/{workspaceId}/saved-filters")
    public ResponseEntity<SavedFilterResponse> create(
            @PathVariable UUID workspaceId,
            @RequestBody SavedFilterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedFilterService.create(workspaceId, request));
    }

    @GetMapping("/saved-filters/{savedFilterId}")
    public SavedFilterResponse get(@PathVariable UUID savedFilterId) {
        return savedFilterService.get(savedFilterId);
    }

    @GetMapping("/saved-filters/{savedFilterId}/work-items")
    public CursorPageResponse<WorkItemResponse> executeWorkItems(
            @PathVariable UUID savedFilterId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return savedFilterExecutionService.executeWorkItems(savedFilterId, limit, cursor);
    }

    @PatchMapping("/saved-filters/{savedFilterId}")
    public SavedFilterResponse update(
            @PathVariable UUID savedFilterId,
            @RequestBody SavedFilterRequest request
    ) {
        return savedFilterService.update(savedFilterId, request);
    }

    @DeleteMapping("/saved-filters/{savedFilterId}")
    public ResponseEntity<Void> delete(@PathVariable UUID savedFilterId) {
        savedFilterService.delete(savedFilterId);
        return ResponseEntity.noContent().build();
    }
}
