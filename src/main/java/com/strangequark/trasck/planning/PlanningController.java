package com.strangequark.trasck.planning;

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
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping("/projects/{projectId}/iterations")
    public List<IterationResponse> listIterations(@PathVariable UUID projectId) {
        return planningService.listIterations(projectId);
    }

    @PostMapping("/projects/{projectId}/iterations")
    public ResponseEntity<IterationResponse> createIteration(
            @PathVariable UUID projectId,
            @RequestBody IterationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.createIteration(projectId, request));
    }

    @GetMapping("/iterations/{iterationId}")
    public IterationResponse getIteration(@PathVariable UUID iterationId) {
        return planningService.getIteration(iterationId);
    }

    @PatchMapping("/iterations/{iterationId}")
    public IterationResponse updateIteration(
            @PathVariable UUID iterationId,
            @RequestBody IterationRequest request
    ) {
        return planningService.updateIteration(iterationId, request);
    }

    @DeleteMapping("/iterations/{iterationId}")
    public ResponseEntity<Void> cancelIteration(@PathVariable UUID iterationId) {
        planningService.cancelIteration(iterationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/iterations/{iterationId}/work-items")
    public List<IterationWorkItemResponse> listIterationWorkItems(@PathVariable UUID iterationId) {
        return planningService.listIterationWorkItems(iterationId);
    }

    @PostMapping("/iterations/{iterationId}/work-items")
    public ResponseEntity<IterationWorkItemResponse> addWorkItem(
            @PathVariable UUID iterationId,
            @RequestBody IterationWorkItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.addWorkItem(iterationId, request));
    }

    @DeleteMapping("/iterations/{iterationId}/work-items/{workItemId}")
    public ResponseEntity<Void> removeWorkItem(@PathVariable UUID iterationId, @PathVariable UUID workItemId) {
        planningService.removeWorkItem(iterationId, workItemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/iterations/{iterationId}/commit")
    public IterationResponse commitIteration(
            @PathVariable UUID iterationId,
            @RequestBody(required = false) IterationCommitRequest request
    ) {
        return planningService.commitIteration(iterationId, request);
    }

    @PostMapping("/iterations/{iterationId}/close")
    public IterationResponse closeIteration(
            @PathVariable UUID iterationId,
            @RequestBody(required = false) IterationCloseRequest request
    ) {
        return planningService.closeIteration(iterationId, request);
    }
}
