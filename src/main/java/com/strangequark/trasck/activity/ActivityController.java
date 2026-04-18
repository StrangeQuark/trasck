package com.strangequark.trasck.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/workspaces/{workspaceId}/activity")
    public List<ActivityEventResponse> workspaceActivity(@PathVariable UUID workspaceId, @RequestParam(required = false) Integer limit) {
        return activityService.workspaceActivity(workspaceId, limit);
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/activity")
    public List<ActivityEventResponse> projectActivity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) Integer limit
    ) {
        return activityService.projectActivity(workspaceId, projectId, limit);
    }

    @GetMapping("/work-items/{workItemId}/activity")
    public List<ActivityEventResponse> workItemActivity(@PathVariable UUID workItemId, @RequestParam(required = false) Integer limit) {
        return activityService.workItemActivity(workItemId, limit);
    }
}
