package com.strangequark.trasck.audit;

import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.integration.ExportFileResponse;
import com.strangequark.trasck.integration.ExportJobResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/workspaces/{workspaceId}/audit-log")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public CursorPageResponse<AuditLogEntryResponse> listAuditLog(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return auditService.listAuditLog(workspaceId, limit, cursor);
    }

    @GetMapping("/workspaces/{workspaceId}/audit-retention-policy")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public AuditRetentionPolicyResponse getRetentionPolicy(@PathVariable UUID workspaceId) {
        return auditService.getRetentionPolicy(workspaceId);
    }

    @PutMapping("/workspaces/{workspaceId}/audit-retention-policy")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public AuditRetentionPolicyResponse updateRetentionPolicy(
            @PathVariable UUID workspaceId,
            @RequestBody AuditRetentionPolicyRequest request
    ) {
        return auditService.updateRetentionPolicy(workspaceId, request);
    }

    @PostMapping("/workspaces/{workspaceId}/audit-retention-policy/export")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public AuditRetentionExportResponse exportRetentionCandidates(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Integer limit
    ) {
        return auditService.exportRetentionCandidates(workspaceId, limit);
    }

    @PostMapping("/workspaces/{workspaceId}/audit-retention-policy/prune")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public AuditRetentionPruneResponse pruneRetentionCandidates(@PathVariable UUID workspaceId) {
        return auditService.pruneRetentionCandidates(workspaceId);
    }

    @GetMapping("/workspaces/{workspaceId}/export-jobs")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public CursorPageResponse<ExportJobResponse> listExportJobs(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String exportType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return auditService.listExportJobs(workspaceId, exportType, limit, cursor);
    }

    @GetMapping("/workspaces/{workspaceId}/export-jobs/{exportJobId}")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public ExportJobResponse getExportJob(@PathVariable UUID workspaceId, @PathVariable UUID exportJobId) {
        return auditService.getExportJob(workspaceId, exportJobId);
    }

    @GetMapping("/workspaces/{workspaceId}/export-jobs/{exportJobId}/download")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public ResponseEntity<byte[]> downloadExportJob(@PathVariable UUID workspaceId, @PathVariable UUID exportJobId) {
        ExportFileResponse file = auditService.downloadExportJob(workspaceId, exportJobId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(file.contentType() == null || file.contentType().isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("Digest", file.checksum())
                .body(file.bytes());
    }
}
