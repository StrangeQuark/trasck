package com.strangequark.trasck.activity;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.api.PageCursorCodec;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import java.util.List;
import java.util.UUID;
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
    public CursorPageResponse<ActivityEventResponse> workspaceActivity(UUID workspaceId, Integer limit, String cursor) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return find(workspaceId, "workspace", workspaceId, limit, cursor);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ActivityEventResponse> projectActivity(UUID workspaceId, UUID projectId, Integer limit, String cursor) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, projectId, "project.read");
        return find(workspaceId, "project", projectId, limit, cursor);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ActivityEventResponse> workItemActivity(UUID workItemId, Integer limit, String cursor) {
        WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.read");
        return find(item.getWorkspaceId(), "work_item", workItemId, limit, cursor);
    }

    private CursorPageResponse<ActivityEventResponse> find(UUID workspaceId, String entityType, UUID entityId, Integer limit, String cursor) {
        int pageLimit = normalizeLimit(limit);
        PageCursorCodec.TimestampCursor decoded = cursor == null || cursor.isBlank() ? null : PageCursorCodec.decodeTimestamp(cursor);
        List<ActivityEvent> page = decoded == null
                ? activityEventRepository.findFirstCursorPage(workspaceId, entityType, entityId, pageLimit + 1)
                : activityEventRepository.findCursorPageAfter(
                        workspaceId,
                        entityType,
                        entityId,
                        decoded.createdAt(),
                        decoded.id(),
                        pageLimit + 1
                );
        boolean hasMore = page.size() > pageLimit;
        List<ActivityEvent> items = hasMore ? page.subList(0, pageLimit) : page;
        String nextCursor = hasMore
                ? PageCursorCodec.encodeTimestamp(items.get(items.size() - 1).getCreatedAt(), items.get(items.size() - 1).getId().toString())
                : null;
        return new CursorPageResponse<>(
                items.stream()
                .map(ActivityEventResponse::from)
                        .toList(),
                nextCursor,
                hasMore,
                pageLimit
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 100));
    }
}
