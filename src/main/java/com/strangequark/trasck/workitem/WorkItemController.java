package com.strangequark.trasck.workitem;

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
public class WorkItemController {

    private final WorkItemService workItemService;

    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    @PostMapping("/projects/{projectId}/work-items")
    public ResponseEntity<WorkItemResponse> create(
            @PathVariable UUID projectId,
            @RequestBody WorkItemCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workItemService.create(projectId, request));
    }

    @GetMapping("/projects/{projectId}/work-items")
    public List<WorkItemResponse> listByProject(@PathVariable UUID projectId) {
        return workItemService.listByProject(projectId);
    }

    @GetMapping("/work-items/{workItemId}")
    public WorkItemResponse get(@PathVariable UUID workItemId) {
        return workItemService.get(workItemId);
    }

    @PatchMapping("/work-items/{workItemId}")
    public WorkItemResponse update(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemUpdateRequest request
    ) {
        return workItemService.update(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/assign")
    public WorkItemResponse assign(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemAssignRequest request
    ) {
        return workItemService.assign(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/rank")
    public WorkItemResponse rank(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemRankRequest request
    ) {
        return workItemService.rank(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/transition")
    public WorkItemResponse transition(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemTransitionRequest request
    ) {
        return workItemService.transition(workItemId, request);
    }

    @DeleteMapping("/work-items/{workItemId}")
    public ResponseEntity<Void> archive(
            @PathVariable UUID workItemId,
            @RequestParam(required = false) UUID actorUserId
    ) {
        workItemService.archive(workItemId, actorUserId);
        return ResponseEntity.noContent().build();
    }
}
