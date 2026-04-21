package com.strangequark.trasck.security;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkspaceSecurityPolicyController {

    private final WorkspaceSecurityPolicyService policyService;

    public WorkspaceSecurityPolicyController(WorkspaceSecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/workspaces/{workspaceId}/security-policy")
    public WorkspaceSecurityPolicyResponse getPolicy(@PathVariable UUID workspaceId) {
        return policyService.getWorkspacePolicy(workspaceId);
    }

    @PatchMapping("/workspaces/{workspaceId}/security-policy")
    public WorkspaceSecurityPolicyResponse updatePolicy(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) WorkspaceSecurityPolicyRequest request
    ) {
        return policyService.updateWorkspacePolicy(workspaceId, request);
    }
}
