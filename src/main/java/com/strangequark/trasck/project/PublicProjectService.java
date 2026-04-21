package com.strangequark.trasck.project;

import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.api.PageCursorCodec;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkItemRepository workItemRepository;

    public PublicProjectService(
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            WorkItemRepository workItemRepository
    ) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workItemRepository = workItemRepository;
    }

    @Transactional(readOnly = true)
    public PublicProjectResponse getPublicProject(UUID projectId) {
        Project project = publicProject(projectId);

        return new PublicProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getKey(),
                project.getDescription(),
                project.getVisibility()
        );
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<PublicWorkItemResponse> listPublicWorkItems(UUID projectId, Integer limit, String cursor) {
        publicProject(projectId);
        int pageLimit = normalizePageLimit(limit);
        PageCursorCodec.RankCursor decoded = hasText(cursor) ? PageCursorCodec.decodeRank(cursor) : null;
        List<WorkItem> page = decoded == null
                ? workItemRepository.findPublicProjectFirstCursorPage(projectId, pageLimit + 1)
                : workItemRepository.findPublicProjectCursorPageAfter(projectId, decoded.rank(), decoded.id(), pageLimit + 1);
        boolean hasMore = page.size() > pageLimit;
        List<WorkItem> items = hasMore ? page.subList(0, pageLimit) : page;
        String nextCursor = hasMore
                ? PageCursorCodec.encodeRank(items.get(items.size() - 1).getRank(), items.get(items.size() - 1).getId().toString())
                : null;
        return new CursorPageResponse<>(
                items.stream().map(PublicWorkItemResponse::from).toList(),
                nextCursor,
                hasMore,
                pageLimit
        );
    }

    @Transactional(readOnly = true)
    public PublicWorkItemResponse getPublicWorkItem(UUID projectId, UUID workItemId) {
        Project project = publicProject(projectId);
        WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .orElseThrow(this::publicProjectNotFound);
        if (!project.getId().equals(item.getProjectId()) || !isPubliclyReadable(item)) {
            throw publicProjectNotFound();
        }
        return PublicWorkItemResponse.from(item);
    }

    private Project publicProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(this::publicProjectNotFound);
        Workspace workspace = workspaceRepository.findById(project.getWorkspaceId())
                .orElseThrow(this::publicProjectNotFound);

        if (!isPubliclyReadable(workspace, project)) {
            throw publicProjectNotFound();
        }
        return project;
    }

    private boolean isPubliclyReadable(Workspace workspace, Project project) {
        return Boolean.TRUE.equals(workspace.getAnonymousReadEnabled())
                && "active".equals(workspace.getStatus())
                && workspace.getDeletedAt() == null
                && "public".equalsIgnoreCase(project.getVisibility())
                && "active".equals(project.getStatus())
                && project.getDeletedAt() == null;
    }

    private boolean isPubliclyReadable(WorkItem item) {
        return item.getDeletedAt() == null && !"private".equals(item.getVisibility());
    }

    private int normalizePageLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResponseStatusException publicProjectNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Public project not found");
    }
}
