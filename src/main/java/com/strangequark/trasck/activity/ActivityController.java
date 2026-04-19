package com.strangequark.trasck.activity;

import com.strangequark.trasck.api.CursorPageResponse;
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
    public CursorPageResponse<ActivityEventResponse> workspaceActivity(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return activityService.workspaceActivity(workspaceId, limit, cursor);
    }

    @GetMapping("/workspaces/{workspaceId}/projects/{projectId}/activity")
    public CursorPageResponse<ActivityEventResponse> projectActivity(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return activityService.projectActivity(workspaceId, projectId, limit, cursor);
    }

    @GetMapping("/work-items/{workItemId}/activity")
    public CursorPageResponse<ActivityEventResponse> workItemActivity(
            @PathVariable UUID workItemId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return activityService.workItemActivity(workItemId, limit, cursor);
    }
}
