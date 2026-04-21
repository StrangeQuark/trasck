package com.strangequark.trasck.project;

import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.workitem.AttachmentFileResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/projects")
public class PublicProjectController {

    private final PublicProjectService publicProjectService;

    public PublicProjectController(PublicProjectService publicProjectService) {
        this.publicProjectService = publicProjectService;
    }

    @GetMapping("/{projectId}")
    public PublicProjectResponse getPublicProject(@PathVariable UUID projectId) {
        return publicProjectService.getPublicProject(projectId);
    }

    @GetMapping("/{projectId}/work-items")
    public CursorPageResponse<PublicWorkItemResponse> listPublicProjectWorkItems(
            @PathVariable UUID projectId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return publicProjectService.listPublicWorkItems(projectId, limit, cursor);
    }

    @GetMapping("/{projectId}/work-items/{workItemId}")
    public PublicWorkItemResponse getPublicProjectWorkItem(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId
    ) {
        return publicProjectService.getPublicWorkItem(projectId, workItemId);
    }

    @GetMapping("/{projectId}/work-items/{workItemId}/comments")
    public List<PublicWorkItemCommentResponse> listPublicProjectWorkItemComments(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId
    ) {
        return publicProjectService.listPublicWorkItemComments(projectId, workItemId);
    }

    @GetMapping("/{projectId}/work-items/{workItemId}/attachments")
    public List<PublicWorkItemAttachmentResponse> listPublicProjectWorkItemAttachments(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId
    ) {
        return publicProjectService.listPublicWorkItemAttachments(projectId, workItemId);
    }

    @GetMapping("/{projectId}/work-items/{workItemId}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadPublicProjectWorkItemAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @PathVariable UUID attachmentId,
            @RequestParam String token
    ) {
        AttachmentFileResponse file = publicProjectService.downloadPublicWorkItemAttachment(projectId, workItemId, attachmentId, token);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
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
