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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/workspaces/{workspaceId}/dashboards")
    public List<DashboardResponse> list(@PathVariable UUID workspaceId) {
        return dashboardService.list(workspaceId);
    }

    @GetMapping("/projects/{projectId}/dashboards")
    public List<DashboardResponse> listByProject(@PathVariable UUID projectId) {
        return dashboardService.listByProject(projectId);
    }

    @GetMapping("/teams/{teamId}/dashboards")
    public List<DashboardResponse> listByTeam(@PathVariable UUID teamId) {
        return dashboardService.listByTeam(teamId);
    }

    @PostMapping("/workspaces/{workspaceId}/dashboards")
    public ResponseEntity<DashboardResponse> create(
            @PathVariable UUID workspaceId,
            @RequestBody DashboardRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboardService.create(workspaceId, request));
    }

    @GetMapping("/dashboards/{dashboardId}")
    public DashboardResponse get(@PathVariable UUID dashboardId) {
        return dashboardService.get(dashboardId);
    }

    @PatchMapping("/dashboards/{dashboardId}")
    public DashboardResponse update(
            @PathVariable UUID dashboardId,
            @RequestBody DashboardRequest request
    ) {
        return dashboardService.update(dashboardId, request);
    }

    @DeleteMapping("/dashboards/{dashboardId}")
    public ResponseEntity<Void> delete(@PathVariable UUID dashboardId) {
        dashboardService.delete(dashboardId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboards/{dashboardId}/render")
    public DashboardRenderResponse render(
            @PathVariable UUID dashboardId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return dashboardService.render(dashboardId, from, to);
    }

    @PostMapping("/dashboards/{dashboardId}/widgets")
    public ResponseEntity<DashboardWidgetResponse> createWidget(
            @PathVariable UUID dashboardId,
            @RequestBody DashboardWidgetRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dashboardService.createWidget(dashboardId, request));
    }

    @PatchMapping("/dashboards/{dashboardId}/widgets/{widgetId}")
    public DashboardWidgetResponse updateWidget(
            @PathVariable UUID dashboardId,
            @PathVariable UUID widgetId,
            @RequestBody DashboardWidgetRequest request
    ) {
        return dashboardService.updateWidget(dashboardId, widgetId, request);
    }

    @DeleteMapping("/dashboards/{dashboardId}/widgets/{widgetId}")
    public ResponseEntity<Void> deleteWidget(@PathVariable UUID dashboardId, @PathVariable UUID widgetId) {
        dashboardService.deleteWidget(dashboardId, widgetId);
        return ResponseEntity.noContent().build();
    }
}
