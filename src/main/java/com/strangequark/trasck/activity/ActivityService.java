package com.strangequark.trasck.activity;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityService {

    private final ActivityEventRepository activityEventRepository;
    private final WorkItemRepository workItemRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public ActivityService(
            ActivityEventRepository activityEventRepository,
            WorkItemRepository workItemRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService
    ) {
        this.activityEventRepository = activityEventRepository;
        this.workItemRepository = workItemRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<ActivityEventResponse> workspaceActivity(UUID workspaceId, Integer limit) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return find(workspaceId, "workspace", workspaceId, limit);
    }

    @Transactional(readOnly = true)
    public List<ActivityEventResponse> projectActivity(UUID workspaceId, UUID projectId, Integer limit) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.read");
        return find(workspaceId, "project", projectId, limit);
    }

    @Transactional(readOnly = true)
    public List<ActivityEventResponse> workItemActivity(UUID workItemId, Integer limit) {
        WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.read");
        return find(item.getWorkspaceId(), "work_item", workItemId, limit);
    }

    private List<ActivityEventResponse> find(UUID workspaceId, String entityType, UUID entityId, Integer limit) {
        return activityEventRepository.findByWorkspaceIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        workspaceId,
                        entityType,
                        entityId,
                        PageRequest.of(0, normalizeLimit(limit))
                ).stream()
                .map(ActivityEventResponse::from)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 100));
    }
}
