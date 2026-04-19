package com.strangequark.trasck.audit;

import java.util.List;
import java.util.UUID;
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
    public List<AuditLogEntryResponse> listAuditLog(@PathVariable UUID workspaceId, @RequestParam(required = false) Integer limit) {
        return auditService.listAuditLog(workspaceId, limit);
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
}
