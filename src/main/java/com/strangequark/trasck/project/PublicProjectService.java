package com.strangequark.trasck.project;

import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.api.PageCursorCodec;
import com.strangequark.trasck.activity.Attachment;
import com.strangequark.trasck.activity.AttachmentRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.activity.CommentRepository;
import com.strangequark.trasck.activity.storage.AttachmentStorageService;
import com.strangequark.trasck.security.ContentLimitPolicy;
import com.strangequark.trasck.workitem.AttachmentFileResponse;
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
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final PublicAttachmentDownloadTokenService publicAttachmentDownloadTokenService;
    private final ContentLimitPolicy contentLimitPolicy;

    public PublicProjectService(
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            WorkItemRepository workItemRepository,
            CommentRepository commentRepository,
            AttachmentRepository attachmentRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            AttachmentStorageService attachmentStorageService,
            PublicAttachmentDownloadTokenService publicAttachmentDownloadTokenService,
            ContentLimitPolicy contentLimitPolicy
    ) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workItemRepository = workItemRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.publicAttachmentDownloadTokenService = publicAttachmentDownloadTokenService;
        this.contentLimitPolicy = contentLimitPolicy;
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
        return PublicWorkItemResponse.from(publicWorkItem(projectId, workItemId));
    }

    @Transactional(readOnly = true)
    public List<PublicWorkItemCommentResponse> listPublicWorkItemComments(UUID projectId, UUID workItemId) {
        WorkItem item = publicWorkItem(projectId, workItemId);
        return commentRepository.findPublicByWorkItemIdOrderByCreatedAtAsc(item.getId()).stream()
                .map(PublicWorkItemCommentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicWorkItemAttachmentResponse> listPublicWorkItemAttachments(UUID projectId, UUID workItemId) {
        WorkItem item = publicWorkItem(projectId, workItemId);
        return attachmentRepository.findPublicByWorkItemIdOrderByCreatedAtAsc(item.getId()).stream()
                .map(attachment -> PublicWorkItemAttachmentResponse.from(
                        projectId,
                        item.getId(),
                        attachment,
                        publicAttachmentDownloadTokenService.issue(projectId, item.getId(), attachment.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentFileResponse downloadPublicWorkItemAttachment(UUID projectId, UUID workItemId, UUID attachmentId, String token) {
        WorkItem item = publicWorkItem(projectId, workItemId);
        publicAttachmentDownloadTokenService.verify(token, projectId, item.getId(), attachmentId);
        Attachment attachment = attachmentRepository.findPublicByWorkItemIdAndId(item.getId(), attachmentId)
                .orElseThrow(this::publicProjectNotFound);
        contentLimitPolicy.validateAttachmentDownload(item.getWorkspaceId(), item.getProjectId(), attachment.getFilename(), attachment.getContentType(), attachment.getSizeBytes());
        AttachmentStorageConfig storageConfig = resolveExistingStorageConfig(item.getWorkspaceId(), attachment.getStorageConfigId());
        byte[] bytes = attachmentStorageService.read(storageConfig, attachment.getStorageKey());
        contentLimitPolicy.validateAttachmentDownload(item.getWorkspaceId(), item.getProjectId(), attachment.getFilename(), attachment.getContentType(), (long) bytes.length);
        return new AttachmentFileResponse(attachment.getFilename(), attachment.getContentType(), bytes);
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

    private WorkItem publicWorkItem(UUID projectId, UUID workItemId) {
        Project project = publicProject(projectId);
        WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                .orElseThrow(this::publicProjectNotFound);
        if (!project.getId().equals(item.getProjectId()) || !isPubliclyReadable(item)) {
            throw publicProjectNotFound();
        }
        return item;
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

    private AttachmentStorageConfig resolveExistingStorageConfig(UUID workspaceId, UUID storageConfigId) {
        if (storageConfigId == null) {
            throw publicProjectNotFound();
        }
        return attachmentStorageConfigRepository.findByIdAndWorkspaceId(storageConfigId, workspaceId)
                .orElseThrow(this::publicProjectNotFound);
    }

    private ResponseStatusException publicProjectNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Public project not found");
    }
}
