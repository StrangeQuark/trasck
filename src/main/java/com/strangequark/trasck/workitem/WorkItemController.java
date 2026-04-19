package com.strangequark.trasck.workitem;

import com.strangequark.trasck.api.CursorPageResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class WorkItemController {

    private final WorkItemService workItemService;
    private final WorkItemCollaborationService collaborationService;

    public WorkItemController(WorkItemService workItemService, WorkItemCollaborationService collaborationService) {
        this.workItemService = workItemService;
        this.collaborationService = collaborationService;
    }

    @PostMapping("/projects/{projectId}/work-items")
    public ResponseEntity<WorkItemResponse> create(
            @PathVariable UUID projectId,
            @RequestBody WorkItemCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workItemService.create(projectId, request));
    }

    @GetMapping("/projects/{projectId}/work-items")
    public CursorPageResponse<WorkItemResponse> listByProject(
            @PathVariable UUID projectId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return workItemService.listByProject(projectId, limit, cursor);
    }

    @GetMapping("/work-items/{workItemId}")
    public WorkItemResponse get(@PathVariable UUID workItemId) {
        return workItemService.get(workItemId);
    }

    @PatchMapping("/work-items/{workItemId}")
    public WorkItemResponse update(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemUpdateRequest request
    ) {
        return workItemService.update(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/assign")
    public WorkItemResponse assign(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemAssignRequest request
    ) {
        return workItemService.assign(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/team")
    public WorkItemResponse setTeam(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemTeamRequest request
    ) {
        return workItemService.setTeam(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/rank")
    public WorkItemResponse rank(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemRankRequest request
    ) {
        return workItemService.rank(workItemId, request);
    }

    @PostMapping("/work-items/{workItemId}/transition")
    public WorkItemResponse transition(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemTransitionRequest request
    ) {
        return workItemService.transition(workItemId, request);
    }

    @DeleteMapping("/work-items/{workItemId}")
    public ResponseEntity<Void> archive(@PathVariable UUID workItemId) {
        workItemService.archive(workItemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-items/{workItemId}/comments")
    public List<CommentResponse> listComments(@PathVariable UUID workItemId) {
        return collaborationService.listComments(workItemId);
    }

    @PostMapping("/work-items/{workItemId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable UUID workItemId,
            @RequestBody CommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.createComment(workItemId, request));
    }

    @PatchMapping("/work-items/{workItemId}/comments/{commentId}")
    public CommentResponse updateComment(
            @PathVariable UUID workItemId,
            @PathVariable UUID commentId,
            @RequestBody CommentRequest request
    ) {
        return collaborationService.updateComment(workItemId, commentId, request);
    }

    @DeleteMapping("/work-items/{workItemId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID workItemId, @PathVariable UUID commentId) {
        collaborationService.deleteComment(workItemId, commentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-items/{workItemId}/links")
    public List<WorkItemLinkResponse> listLinks(@PathVariable UUID workItemId) {
        return collaborationService.listLinks(workItemId);
    }

    @PostMapping("/work-items/{workItemId}/links")
    public ResponseEntity<WorkItemLinkResponse> createLink(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemLinkRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.createLink(workItemId, request));
    }

    @DeleteMapping("/work-items/{workItemId}/links/{linkId}")
    public ResponseEntity<Void> deleteLink(@PathVariable UUID workItemId, @PathVariable UUID linkId) {
        collaborationService.deleteLink(workItemId, linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-items/{workItemId}/watchers")
    public List<WatcherResponse> listWatchers(@PathVariable UUID workItemId) {
        return collaborationService.listWatchers(workItemId);
    }

    @PostMapping("/work-items/{workItemId}/watchers")
    public ResponseEntity<WatcherResponse> addWatcher(
            @PathVariable UUID workItemId,
            @RequestBody(required = false) WatcherRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.addWatcher(workItemId, request));
    }

    @DeleteMapping("/work-items/{workItemId}/watchers/{userId}")
    public ResponseEntity<Void> removeWatcher(@PathVariable UUID workItemId, @PathVariable UUID userId) {
        collaborationService.removeWatcher(workItemId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-items/{workItemId}/work-logs")
    public List<WorkLogResponse> listWorkLogs(@PathVariable UUID workItemId) {
        return collaborationService.listWorkLogs(workItemId);
    }

    @PostMapping("/work-items/{workItemId}/work-logs")
    public ResponseEntity<WorkLogResponse> createWorkLog(
            @PathVariable UUID workItemId,
            @RequestBody WorkLogRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.createWorkLog(workItemId, request));
    }

    @PatchMapping("/work-items/{workItemId}/work-logs/{workLogId}")
    public WorkLogResponse updateWorkLog(
            @PathVariable UUID workItemId,
            @PathVariable UUID workLogId,
            @RequestBody WorkLogRequest request
    ) {
        return collaborationService.updateWorkLog(workItemId, workLogId, request);
    }

    @DeleteMapping("/work-items/{workItemId}/work-logs/{workLogId}")
    public ResponseEntity<Void> deleteWorkLog(@PathVariable UUID workItemId, @PathVariable UUID workLogId) {
        collaborationService.deleteWorkLog(workItemId, workLogId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workspaces/{workspaceId}/labels")
    public List<LabelResponse> listWorkspaceLabels(@PathVariable UUID workspaceId) {
        return collaborationService.listWorkspaceLabels(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/labels")
    public ResponseEntity<LabelResponse> createWorkspaceLabel(
            @PathVariable UUID workspaceId,
            @RequestBody LabelRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.createWorkspaceLabel(workspaceId, request));
    }

    @GetMapping("/work-items/{workItemId}/labels")
    public List<LabelResponse> listWorkItemLabels(@PathVariable UUID workItemId) {
        return collaborationService.listWorkItemLabels(workItemId);
    }

    @PostMapping("/work-items/{workItemId}/labels")
    public ResponseEntity<LabelResponse> addLabel(
            @PathVariable UUID workItemId,
            @RequestBody WorkItemLabelRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.addLabel(workItemId, request));
    }

    @DeleteMapping("/work-items/{workItemId}/labels/{labelId}")
    public ResponseEntity<Void> removeLabel(@PathVariable UUID workItemId, @PathVariable UUID labelId) {
        collaborationService.removeLabel(workItemId, labelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/work-items/{workItemId}/attachments")
    public List<AttachmentResponse> listAttachments(@PathVariable UUID workItemId) {
        return collaborationService.listAttachments(workItemId);
    }

    @PostMapping("/work-items/{workItemId}/attachments")
    public ResponseEntity<AttachmentResponse> addAttachmentMetadata(
            @PathVariable UUID workItemId,
            @RequestBody AttachmentMetadataRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(collaborationService.addAttachmentMetadata(workItemId, request));
    }

    @PostMapping(value = "/work-items/{workItemId}/attachments/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @PathVariable UUID workItemId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID storageConfigId,
            @RequestParam(required = false) String checksum,
            @RequestParam(required = false) String visibility
    ) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = file.getName();
        }
        try {
            AttachmentResponse response = collaborationService.uploadAttachment(
                    workItemId,
                    filename,
                    file.getContentType(),
                    file.getBytes(),
                    storageConfigId,
                    checksum,
                    visibility
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file could not be read", ex);
        }
    }

    @GetMapping("/work-items/{workItemId}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID workItemId, @PathVariable UUID attachmentId) {
        AttachmentFileResponse file = collaborationService.downloadAttachment(workItemId, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
    }

    @DeleteMapping("/work-items/{workItemId}/attachments/{attachmentId}")
    public ResponseEntity<Void> removeAttachment(@PathVariable UUID workItemId, @PathVariable UUID attachmentId) {
        collaborationService.removeAttachment(workItemId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
