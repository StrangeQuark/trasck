package com.strangequark.trasck.search;

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
public class PersonalizationController {

    private final PersonalizationService personalizationService;

    public PersonalizationController(PersonalizationService personalizationService) {
        this.personalizationService = personalizationService;
    }

    @GetMapping("/workspaces/{workspaceId}/personalization/views")
    public List<SavedViewResponse> listViews(@PathVariable UUID workspaceId) {
        return personalizationService.listViews(workspaceId);
    }

    @GetMapping("/projects/{projectId}/personalization/views")
    public List<SavedViewResponse> listProjectViews(@PathVariable UUID projectId) {
        return personalizationService.listViewsByProject(projectId);
    }

    @GetMapping("/teams/{teamId}/personalization/views")
    public List<SavedViewResponse> listTeamViews(@PathVariable UUID teamId) {
        return personalizationService.listViewsByTeam(teamId);
    }

    @PostMapping("/workspaces/{workspaceId}/personalization/views")
    public ResponseEntity<SavedViewResponse> createView(
            @PathVariable UUID workspaceId,
            @RequestBody SavedViewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personalizationService.createView(workspaceId, request));
    }

    @GetMapping("/personalization/views/{viewId}")
    public SavedViewResponse getView(@PathVariable UUID viewId) {
        return personalizationService.getView(viewId);
    }

    @PatchMapping("/personalization/views/{viewId}")
    public SavedViewResponse updateView(
            @PathVariable UUID viewId,
            @RequestBody SavedViewRequest request
    ) {
        return personalizationService.updateView(viewId, request);
    }

    @DeleteMapping("/personalization/views/{viewId}")
    public ResponseEntity<Void> deleteView(@PathVariable UUID viewId) {
        personalizationService.deleteView(viewId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/personalization/favorites")
    public List<FavoriteResponse> listFavorites(@PathVariable UUID workspaceId) {
        return personalizationService.listFavorites(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/personalization/favorites")
    public ResponseEntity<FavoriteResponse> addFavorite(
            @PathVariable UUID workspaceId,
            @RequestBody FavoriteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personalizationService.addFavorite(workspaceId, request));
    }

    @DeleteMapping("/personalization/favorites/{favoriteId}")
    public ResponseEntity<Void> deleteFavorite(@PathVariable UUID favoriteId) {
        personalizationService.deleteFavorite(favoriteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/personalization/recent-items")
    public List<RecentItemResponse> listRecentItems(@PathVariable UUID workspaceId) {
        return personalizationService.listRecentItems(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/personalization/recent-items")
    public RecentItemResponse recordRecentItem(
            @PathVariable UUID workspaceId,
            @RequestBody RecentItemRequest request
    ) {
        return personalizationService.recordRecentItem(workspaceId, request);
    }

    @DeleteMapping("/personalization/recent-items/{recentItemId}")
    public ResponseEntity<Void> deleteRecentItem(@PathVariable UUID recentItemId) {
        personalizationService.deleteRecentItem(recentItemId);
        return ResponseEntity.noContent().build();
    }
}
