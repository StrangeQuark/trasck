package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentProviderController {

    private final AgentService agentService;

    public AgentProviderController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/workspaces/{workspaceId}/agent-providers")
    public List<AgentProviderResponse> listProviders(@PathVariable UUID workspaceId) {
        return agentService.listProviders(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-providers")
    public ResponseEntity<AgentProviderResponse> createProvider(
            @PathVariable UUID workspaceId,
            @RequestBody AgentProviderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createProvider(workspaceId, request));
    }

    @PatchMapping("/agent-providers/{providerId}")
    public AgentProviderResponse updateProvider(
            @PathVariable UUID providerId,
            @RequestBody AgentProviderRequest request
    ) {
        return agentService.updateProvider(providerId, request);
    }

    @PostMapping("/agent-providers/{providerId}/deactivate")
    public AgentProviderResponse deactivateProvider(@PathVariable UUID providerId) {
        return agentService.deactivateProvider(providerId);
    }

    @PostMapping("/agent-providers/{providerId}/runtime-preview")
    public AgentRuntimePreviewResponse previewRuntime(
            @PathVariable UUID providerId,
            @RequestBody(required = false) AgentRuntimePreviewRequest request
    ) {
        return agentService.previewRuntime(providerId, request);
    }

    @PostMapping("/agent-providers/{providerId}/credentials")
    public ResponseEntity<AgentProviderCredentialResponse> createCredential(
            @PathVariable UUID providerId,
            @RequestBody AgentProviderCredentialRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createCredential(providerId, request));
    }

    @GetMapping("/agent-providers/{providerId}/credentials")
    public List<AgentProviderCredentialResponse> listCredentials(@PathVariable UUID providerId) {
        return agentService.listCredentials(providerId);
    }

    @PostMapping("/agent-providers/{providerId}/credentials/{credentialId}/deactivate")
    public AgentProviderCredentialResponse deactivateCredential(
            @PathVariable UUID providerId,
            @PathVariable UUID credentialId
    ) {
        return agentService.deactivateCredential(providerId, credentialId);
    }

    @PostMapping("/agent-providers/{providerId}/credentials/reencrypt")
    public List<AgentProviderCredentialResponse> reencryptCredentials(@PathVariable UUID providerId) {
        return agentService.reencryptCredentials(providerId);
    }

    @PostMapping("/agent-providers/{providerId}/callback-keys/rotate")
    public AgentProviderResponse rotateCallbackKey(@PathVariable UUID providerId) {
        return agentService.rotateCallbackKey(providerId);
    }
}
