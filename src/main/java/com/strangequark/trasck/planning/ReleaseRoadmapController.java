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
public class ReleaseRoadmapController {

    private final ReleaseRoadmapService releaseRoadmapService;

    public ReleaseRoadmapController(ReleaseRoadmapService releaseRoadmapService) {
        this.releaseRoadmapService = releaseRoadmapService;
    }

    @GetMapping("/projects/{projectId}/releases")
    public List<ReleaseResponse> listReleases(@PathVariable UUID projectId) {
        return releaseRoadmapService.listReleases(projectId);
    }

    @PostMapping("/projects/{projectId}/releases")
    public ResponseEntity<ReleaseResponse> createRelease(@PathVariable UUID projectId, @RequestBody ReleaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(releaseRoadmapService.createRelease(projectId, request));
    }

    @GetMapping("/releases/{releaseId}")
    public ReleaseResponse getRelease(@PathVariable UUID releaseId) {
        return releaseRoadmapService.getRelease(releaseId);
    }

    @PatchMapping("/releases/{releaseId}")
    public ReleaseResponse updateRelease(@PathVariable UUID releaseId, @RequestBody ReleaseRequest request) {
        return releaseRoadmapService.updateRelease(releaseId, request);
    }

    @DeleteMapping("/releases/{releaseId}")
    public ResponseEntity<Void> deleteRelease(@PathVariable UUID releaseId) {
        releaseRoadmapService.deleteRelease(releaseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/releases/{releaseId}/work-items")
    public List<ReleaseWorkItemResponse> listReleaseWorkItems(@PathVariable UUID releaseId) {
        return releaseRoadmapService.listReleaseWorkItems(releaseId);
    }

    @PostMapping("/releases/{releaseId}/work-items")
    public ResponseEntity<ReleaseWorkItemResponse> addReleaseWorkItem(
            @PathVariable UUID releaseId,
            @RequestBody ReleaseWorkItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(releaseRoadmapService.addReleaseWorkItem(releaseId, request));
    }

    @DeleteMapping("/releases/{releaseId}/work-items/{workItemId}")
    public ResponseEntity<Void> removeReleaseWorkItem(@PathVariable UUID releaseId, @PathVariable UUID workItemId) {
        releaseRoadmapService.removeReleaseWorkItem(releaseId, workItemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/roadmaps")
    public List<RoadmapResponse> listWorkspaceRoadmaps(@PathVariable UUID workspaceId) {
        return releaseRoadmapService.listWorkspaceRoadmaps(workspaceId);
    }

    @GetMapping("/projects/{projectId}/roadmaps")
    public List<RoadmapResponse> listProjectRoadmaps(@PathVariable UUID projectId) {
        return releaseRoadmapService.listProjectRoadmaps(projectId);
    }

    @PostMapping("/workspaces/{workspaceId}/roadmaps")
    public ResponseEntity<RoadmapResponse> createRoadmap(@PathVariable UUID workspaceId, @RequestBody RoadmapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(releaseRoadmapService.createRoadmap(workspaceId, request));
    }

    @GetMapping("/roadmaps/{roadmapId}")
    public RoadmapResponse getRoadmap(@PathVariable UUID roadmapId) {
        return releaseRoadmapService.getRoadmap(roadmapId);
    }

    @PatchMapping("/roadmaps/{roadmapId}")
    public RoadmapResponse updateRoadmap(@PathVariable UUID roadmapId, @RequestBody RoadmapRequest request) {
        return releaseRoadmapService.updateRoadmap(roadmapId, request);
    }

    @DeleteMapping("/roadmaps/{roadmapId}")
    public ResponseEntity<Void> deleteRoadmap(@PathVariable UUID roadmapId) {
        releaseRoadmapService.deleteRoadmap(roadmapId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roadmaps/{roadmapId}/items")
    public List<RoadmapItemResponse> listRoadmapItems(@PathVariable UUID roadmapId) {
        return releaseRoadmapService.listRoadmapItems(roadmapId);
    }

    @PostMapping("/roadmaps/{roadmapId}/items")
    public ResponseEntity<RoadmapItemResponse> createRoadmapItem(
            @PathVariable UUID roadmapId,
            @RequestBody RoadmapItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(releaseRoadmapService.createRoadmapItem(roadmapId, request));
    }

    @PatchMapping("/roadmaps/{roadmapId}/items/{roadmapItemId}")
    public RoadmapItemResponse updateRoadmapItem(
            @PathVariable UUID roadmapId,
            @PathVariable UUID roadmapItemId,
            @RequestBody RoadmapItemRequest request
    ) {
        return releaseRoadmapService.updateRoadmapItem(roadmapId, roadmapItemId, request);
    }

    @DeleteMapping("/roadmaps/{roadmapId}/items/{roadmapItemId}")
    public ResponseEntity<Void> deleteRoadmapItem(@PathVariable UUID roadmapId, @PathVariable UUID roadmapItemId) {
        releaseRoadmapService.deleteRoadmapItem(roadmapId, roadmapItemId);
        return ResponseEntity.noContent().build();
    }
}
