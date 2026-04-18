package com.strangequark.trasck.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.activity.Attachment;
import com.strangequark.trasck.activity.AttachmentRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.activity.Comment;
import com.strangequark.trasck.activity.CommentRepository;
import com.strangequark.trasck.activity.Watcher;
import com.strangequark.trasck.activity.WatcherId;
import com.strangequark.trasck.activity.WatcherRepository;
import com.strangequark.trasck.activity.WorkItemAttachment;
import com.strangequark.trasck.activity.WorkItemAttachmentId;
import com.strangequark.trasck.activity.WorkItemAttachmentRepository;
import com.strangequark.trasck.activity.storage.AttachmentStorageService;
import com.strangequark.trasck.activity.storage.AttachmentUpload;
import com.strangequark.trasck.activity.storage.StoredAttachment;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkItemCollaborationService {

    private final ObjectMapper objectMapper;
    private final WorkItemRepository workItemRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final WorkItemLinkRepository workItemLinkRepository;
    private final WatcherRepository watcherRepository;
    private final LabelRepository labelRepository;
    private final WorkItemLabelRepository workItemLabelRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final WorkItemAttachmentRepository workItemAttachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public WorkItemCollaborationService(
            ObjectMapper objectMapper,
            WorkItemRepository workItemRepository,
            UserRepository userRepository,
            CommentRepository commentRepository,
            WorkItemLinkRepository workItemLinkRepository,
            WatcherRepository watcherRepository,
            LabelRepository labelRepository,
            WorkItemLabelRepository workItemLabelRepository,
            AttachmentRepository attachmentRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            WorkItemAttachmentRepository workItemAttachmentRepository,
            AttachmentStorageService attachmentStorageService,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.workItemRepository = workItemRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.workItemLinkRepository = workItemLinkRepository;
        this.watcherRepository = watcherRepository;
        this.labelRepository = labelRepository;
        this.workItemLabelRepository = workItemLabelRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.workItemAttachmentRepository = workItemAttachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(UUID workItemId) {
        WorkItem item = readableWorkItem(workItemId);
        requireProjectPermission(item, "work_item.read");
        return commentRepository.findByWorkItemIdAndDeletedAtIsNullOrderByCreatedAtAsc(workItemId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse createComment(UUID workItemId, CommentRequest request) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.comment");
        CommentRequest createRequest = required(request, "request");
        Comment comment = new Comment();
        comment.setWorkItemId(item.getId());
        comment.setAuthorId(actorId);
        comment.setBodyMarkdown(requiredText(createRequest.bodyMarkdown(), "bodyMarkdown"));
        comment.setBodyDocument(toJsonNode(createRequest.bodyDocument()));
        comment.setVisibility(normalizeCommentVisibility(createRequest.visibility()));
        Comment saved = commentRepository.save(comment);
        recordWorkItemEvent(item, "work_item.comment_created", actorId, "commentId", saved.getId());
        return CommentResponse.from(saved);
    }

    @Transactional
    public CommentResponse updateComment(UUID workItemId, UUID commentId, CommentRequest request) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = currentUserService.requireUserId();
        CommentRequest updateRequest = required(request, "request");
        Comment comment = commentRepository.findByIdAndWorkItemIdAndDeletedAtIsNull(commentId, workItemId)
                .orElseThrow(() -> notFound("Comment not found"));
        requireCommentMutationPermission(item, comment, actorId);
        if (updateRequest.bodyMarkdown() != null) {
            comment.setBodyMarkdown(requiredText(updateRequest.bodyMarkdown(), "bodyMarkdown"));
        }
        if (updateRequest.bodyDocument() != null) {
            comment.setBodyDocument(toJsonNode(updateRequest.bodyDocument()));
        }
        if (updateRequest.visibility() != null) {
            comment.setVisibility(normalizeCommentVisibility(updateRequest.visibility()));
        }
        Comment saved = commentRepository.save(comment);
        recordWorkItemEvent(item, "work_item.comment_updated", actorId, "commentId", saved.getId());
        return CommentResponse.from(saved);
    }

    @Transactional
    public void deleteComment(UUID workItemId, UUID commentId) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = currentUserService.requireUserId();
        Comment comment = commentRepository.findByIdAndWorkItemIdAndDeletedAtIsNull(commentId, workItemId)
                .orElseThrow(() -> notFound("Comment not found"));
        requireCommentMutationPermission(item, comment, actorId);
        comment.setDeletedAt(OffsetDateTime.now());
        recordWorkItemEvent(item, "work_item.comment_deleted", actorId, "commentId", comment.getId());
    }

    @Transactional(readOnly = true)
    public List<WorkItemLinkResponse> listLinks(UUID workItemId) {
        WorkItem item = readableWorkItem(workItemId);
        requireProjectPermission(item, "work_item.read");
        return workItemLinkRepository.findBySourceWorkItemIdOrTargetWorkItemIdOrderByCreatedAtAsc(workItemId, workItemId).stream()
                .map(WorkItemLinkResponse::from)
                .toList();
    }

    @Transactional
    public WorkItemLinkResponse createLink(UUID workItemId, WorkItemLinkRequest request) {
        WorkItem source = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(source, "work_item.link");
        WorkItemLinkRequest createRequest = required(request, "request");
        WorkItem target = readableWorkItem(required(createRequest.targetWorkItemId(), "targetWorkItemId"));
        permissionService.requireProjectPermission(actorId, target.getProjectId(), "work_item.read");
        String linkType = requiredText(createRequest.linkType(), "linkType").toLowerCase();
        if (source.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A work item cannot link to itself");
        }
        if (workItemLinkRepository.existsBySourceWorkItemIdAndTargetWorkItemIdAndLinkType(source.getId(), target.getId(), linkType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work item link already exists");
        }
        WorkItemLink link = new WorkItemLink();
        link.setSourceWorkItemId(source.getId());
        link.setTargetWorkItemId(target.getId());
        link.setLinkType(linkType);
        link.setCreatedById(actorId);
        WorkItemLink saved = workItemLinkRepository.save(link);
        recordWorkItemEvent(source, "work_item.link_created", actorId, "linkId", saved.getId());
        return WorkItemLinkResponse.from(saved);
    }

    @Transactional
    public void deleteLink(UUID workItemId, UUID linkId) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.link");
        WorkItemLink link = workItemLinkRepository.findByIdAndWorkItemId(linkId, workItemId)
                .orElseThrow(() -> notFound("Work item link not found"));
        workItemLinkRepository.delete(link);
        recordWorkItemEvent(item, "work_item.link_deleted", actorId, "linkId", link.getId());
    }

    @Transactional(readOnly = true)
    public List<WatcherResponse> listWatchers(UUID workItemId) {
        WorkItem item = readableWorkItem(workItemId);
        requireProjectPermission(item, "work_item.read");
        return watcherRepository.findByIdWorkItemIdOrderByCreatedAtAsc(workItemId).stream()
                .map(WatcherResponse::from)
                .toList();
    }

    @Transactional
    public WatcherResponse addWatcher(UUID workItemId, WatcherRequest request) {
        WorkItem item = readableWorkItem(workItemId);
        UUID currentUserId = currentUserService.requireUserId();
        UUID watcherUserId = request == null || request.userId() == null ? currentUserId : request.userId();
        UUID actorId = requireWatcherMutationPermission(item, watcherUserId);
        activeUser(watcherUserId);
        Watcher watcher = new Watcher();
        watcher.setId(new WatcherId(workItemId, watcherUserId));
        boolean alreadyWatching = watcherRepository.existsById(watcher.getId());
        Watcher saved = alreadyWatching ? watcherRepository.findById(watcher.getId()).orElseThrow() : watcherRepository.save(watcher);
        if (!alreadyWatching) {
            recordWorkItemEvent(item, "work_item.watcher_added", actorId, "watcherUserId", watcherUserId);
        }
        return WatcherResponse.from(saved);
    }

    @Transactional
    public void removeWatcher(UUID workItemId, UUID userId) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireWatcherMutationPermission(item, required(userId, "userId"));
        WatcherId id = new WatcherId(workItemId, userId);
        if (watcherRepository.existsById(id)) {
            watcherRepository.deleteById(id);
            recordWorkItemEvent(item, "work_item.watcher_removed", actorId, "watcherUserId", userId);
        }
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listWorkspaceLabels(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "work_item.read");
        return labelRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Transactional
    public LabelResponse createWorkspaceLabel(UUID workspaceId, LabelRequest request) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, workspaceId, "work_item.update");
        LabelMutation label = createOrFindLabel(workspaceId, required(request, "request"));
        if (label.created()) {
            recordLabelEvent(label.label(), "label.created", actorId);
        }
        return LabelResponse.from(label.label());
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listWorkItemLabels(UUID workItemId) {
        WorkItem item = readableWorkItem(workItemId);
        requireProjectPermission(item, "work_item.read");
        List<UUID> labelIds = workItemLabelRepository.findByIdWorkItemId(workItemId).stream()
                .map(join -> join.getId().getLabelId())
                .toList();
        return labelRepository.findAllById(labelIds).stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Transactional
    public LabelResponse addLabel(UUID workItemId, WorkItemLabelRequest request) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.update");
        WorkItemLabelRequest labelRequest = required(request, "request");
        LabelMutation label = resolveLabel(item.getWorkspaceId(), labelRequest);
        if (label.created()) {
            recordLabelEvent(label.label(), "label.created", actorId);
        }
        WorkItemLabelId id = new WorkItemLabelId(workItemId, label.label().getId());
        if (!workItemLabelRepository.existsById(id)) {
            WorkItemLabel join = new WorkItemLabel();
            join.setId(id);
            workItemLabelRepository.save(join);
            recordWorkItemEvent(item, "work_item.label_added", actorId, "labelId", label.label().getId());
        }
        return LabelResponse.from(label.label());
    }

    @Transactional
    public void removeLabel(UUID workItemId, UUID labelId) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.update");
        WorkItemLabelId id = new WorkItemLabelId(workItemId, labelId);
        if (workItemLabelRepository.existsById(id)) {
            workItemLabelRepository.deleteById(id);
            recordWorkItemEvent(item, "work_item.label_removed", actorId, "labelId", labelId);
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(UUID workItemId) {
        WorkItem item = readableWorkItem(workItemId);
        requireProjectPermission(item, "work_item.read");
        List<UUID> attachmentIds = workItemAttachmentRepository.findByIdWorkItemId(workItemId).stream()
                .map(join -> join.getId().getAttachmentId())
                .toList();
        return attachmentRepository.findByIdInOrderByCreatedAtAsc(attachmentIds).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional
    public AttachmentResponse addAttachmentMetadata(UUID workItemId, AttachmentMetadataRequest request) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.update");
        AttachmentMetadataRequest metadata = required(request, "request");
        Attachment attachment = new Attachment();
        attachment.setWorkspaceId(item.getWorkspaceId());
        attachment.setStorageConfigId(resolveStorageConfigId(item.getWorkspaceId(), metadata.storageConfigId()));
        attachment.setUploaderId(actorId);
        attachment.setFilename(requiredText(metadata.filename(), "filename"));
        attachment.setContentType(metadata.contentType());
        attachment.setStorageKey(requiredText(metadata.storageKey(), "storageKey"));
        Long sizeBytes = required(metadata.sizeBytes(), "sizeBytes");
        if (sizeBytes < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sizeBytes must be greater than or equal to 0");
        }
        attachment.setSizeBytes(sizeBytes);
        attachment.setChecksum(metadata.checksum());
        attachment.setVisibility(normalizeAttachmentVisibility(metadata.visibility()));
        Attachment saved = attachmentRepository.save(attachment);
        WorkItemAttachment join = new WorkItemAttachment();
        join.setId(new WorkItemAttachmentId(workItemId, saved.getId()));
        workItemAttachmentRepository.save(join);
        recordWorkItemEvent(item, "work_item.attachment_added", actorId, "attachmentId", saved.getId());
        return AttachmentResponse.from(saved);
    }

    @Transactional
    public AttachmentResponse uploadAttachment(
            UUID workItemId,
            String filename,
            String contentType,
            byte[] content,
            UUID storageConfigId,
            String checksum,
            String visibility
    ) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.update");
        AttachmentStorageConfig storageConfig = resolveActiveStorageConfig(item.getWorkspaceId(), storageConfigId);
        StoredAttachment stored = attachmentStorageService.store(
                storageConfig,
                new AttachmentUpload(requiredText(filename, "filename"), blankToNull(contentType), required(content, "file"), checksum)
        );
        try {
            Attachment attachment = new Attachment();
            attachment.setWorkspaceId(item.getWorkspaceId());
            attachment.setStorageConfigId(storageConfig.getId());
            attachment.setUploaderId(actorId);
            attachment.setFilename(requiredText(filename, "filename"));
            attachment.setContentType(blankToNull(contentType));
            attachment.setStorageKey(stored.storageKey());
            attachment.setSizeBytes(stored.sizeBytes());
            attachment.setChecksum(stored.checksum());
            attachment.setVisibility(normalizeAttachmentVisibility(visibility));
            Attachment saved = attachmentRepository.save(attachment);
            WorkItemAttachment join = new WorkItemAttachment();
            join.setId(new WorkItemAttachmentId(workItemId, saved.getId()));
            workItemAttachmentRepository.save(join);
            recordWorkItemEvent(item, "work_item.attachment_added", actorId, "attachmentId", saved.getId());
            return AttachmentResponse.from(saved);
        } catch (RuntimeException ex) {
            attachmentStorageService.delete(storageConfig, stored.storageKey());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public AttachmentFileResponse downloadAttachment(UUID workItemId, UUID attachmentId) {
        WorkItem item = readableWorkItem(workItemId);
        requireProjectPermission(item, "work_item.read");
        Attachment attachment = attachmentForWorkItem(workItemId, attachmentId);
        AttachmentStorageConfig storageConfig = resolveExistingStorageConfig(item.getWorkspaceId(), attachment.getStorageConfigId());
        byte[] bytes = attachmentStorageService.read(storageConfig, attachment.getStorageKey());
        return new AttachmentFileResponse(attachment.getFilename(), attachment.getContentType(), bytes);
    }

    @Transactional
    public void removeAttachment(UUID workItemId, UUID attachmentId) {
        WorkItem item = readableWorkItem(workItemId);
        UUID actorId = requireProjectPermission(item, "work_item.update");
        WorkItemAttachmentId id = new WorkItemAttachmentId(workItemId, attachmentId);
        if (workItemAttachmentRepository.existsById(id)) {
            Attachment attachment = attachmentRepository.findById(attachmentId).orElseThrow(() -> notFound("Attachment not found"));
            boolean removeBytes = workItemAttachmentRepository.countByIdAttachmentId(attachmentId) <= 1;
            workItemAttachmentRepository.deleteById(id);
            if (removeBytes) {
                deleteAttachmentBytes(item.getWorkspaceId(), attachment);
                attachmentRepository.delete(attachment);
            }
            recordWorkItemEvent(item, "work_item.attachment_removed", actorId, "attachmentId", attachmentId);
        }
    }

    private WorkItem readableWorkItem(UUID workItemId) {
        return workItemRepository.findByIdAndDeletedAtIsNull(workItemId).orElseThrow(() -> notFound("Work item not found"));
    }

    private UUID requireProjectPermission(WorkItem item, String permissionKey) {
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireProjectPermission(actorId, item.getProjectId(), permissionKey);
        return actorId;
    }

    private void requireCommentMutationPermission(WorkItem item, Comment comment, UUID actorId) {
        String permissionKey = actorId.equals(comment.getAuthorId()) ? "work_item.comment" : "work_item.update";
        permissionService.requireProjectPermission(actorId, item.getProjectId(), permissionKey);
    }

    private UUID requireWatcherMutationPermission(WorkItem item, UUID watcherUserId) {
        UUID actorId = currentUserService.requireUserId();
        String permissionKey = actorId.equals(watcherUserId) ? "work_item.read" : "work_item.update";
        permissionService.requireProjectPermission(actorId, item.getProjectId(), permissionKey);
        return actorId;
    }

    private LabelMutation resolveLabel(UUID workspaceId, WorkItemLabelRequest request) {
        if (request.labelId() != null) {
            Label label = labelRepository.findById(request.labelId()).orElseThrow(() -> notFound("Label not found"));
            if (!workspaceId.equals(label.getWorkspaceId())) {
                throw notFound("Label not found");
            }
            return new LabelMutation(label, false);
        }
        return createOrFindLabel(workspaceId, new LabelRequest(request.name(), request.color()));
    }

    private LabelMutation createOrFindLabel(UUID workspaceId, LabelRequest request) {
        String name = requiredText(request.name(), "name");
        return labelRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, name)
                .map(label -> new LabelMutation(label, false))
                .orElseGet(() -> {
                    Label label = new Label();
                    label.setWorkspaceId(workspaceId);
                    label.setName(name);
                    label.setColor(request.color());
                    return new LabelMutation(labelRepository.save(label), true);
                });
    }

    private UUID resolveStorageConfigId(UUID workspaceId, UUID storageConfigId) {
        return resolveActiveStorageConfig(workspaceId, storageConfigId).getId();
    }

    private AttachmentStorageConfig resolveActiveStorageConfig(UUID workspaceId, UUID storageConfigId) {
        if (storageConfigId == null) {
            return attachmentStorageConfigRepository.findFirstByWorkspaceIdAndActiveTrueAndDefaultConfigTrue(workspaceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default attachment storage config not found"));
        }
        return attachmentStorageConfigRepository.findByIdAndWorkspaceIdAndActiveTrue(storageConfigId, workspaceId)
                .orElseThrow(() -> notFound("Attachment storage config not found"));
    }

    private AttachmentStorageConfig resolveExistingStorageConfig(UUID workspaceId, UUID storageConfigId) {
        UUID id = required(storageConfigId, "storageConfigId");
        return attachmentStorageConfigRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> notFound("Attachment storage config not found"));
    }

    private Attachment attachmentForWorkItem(UUID workItemId, UUID attachmentId) {
        if (!workItemAttachmentRepository.existsById(new WorkItemAttachmentId(workItemId, attachmentId))) {
            throw notFound("Attachment not found");
        }
        return attachmentRepository.findById(attachmentId).orElseThrow(() -> notFound("Attachment not found"));
    }

    private void deleteAttachmentBytes(UUID workspaceId, Attachment attachment) {
        if (attachment.getStorageConfigId() == null) {
            return;
        }
        AttachmentStorageConfig storageConfig = resolveExistingStorageConfig(workspaceId, attachment.getStorageConfigId());
        attachmentStorageService.delete(storageConfig, attachment.getStorageKey());
    }

    private void recordWorkItemEvent(WorkItem item, String eventType, UUID actorId, String relatedIdName, UUID relatedId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("projectId", item.getProjectId().toString())
                .put("actorUserId", actorId.toString());
        if (relatedId != null) {
            payload.put(relatedIdName, relatedId.toString());
        }
        domainEventService.record(item.getWorkspaceId(), "work_item", item.getId(), eventType, payload);
    }

    private void recordLabelEvent(Label label, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("labelId", label.getId().toString())
                .put("workspaceId", label.getWorkspaceId().toString())
                .put("name", label.getName())
                .put("actorUserId", actorId.toString());
        domainEventService.record(label.getWorkspaceId(), "label", label.getId(), eventType, payload);
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> notFound("User not found"));
        if (!Boolean.TRUE.equals(user.getActive()) || user.getDeletedAt() != null) {
            throw notFound("User not found");
        }
        return user;
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private String normalizeCommentVisibility(String visibility) {
        String normalized = visibility == null || visibility.isBlank() ? "workspace" : visibility.trim().toLowerCase();
        if (!normalized.equals("workspace") && !normalized.equals("public") && !normalized.equals("private")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility must be workspace, public, or private");
        }
        return normalized;
    }

    private String normalizeAttachmentVisibility(String visibility) {
        String normalized = visibility == null || visibility.isBlank() ? "restricted" : visibility.trim().toLowerCase();
        if (!normalized.equals("restricted") && !normalized.equals("public")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility must be restricted or public");
        }
        return normalized;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record LabelMutation(Label label, boolean created) {
    }
}
