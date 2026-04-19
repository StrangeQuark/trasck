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
public class AgentProfileController {

    private final AgentService agentService;

    public AgentProfileController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/workspaces/{workspaceId}/agents")
    public List<AgentProfileResponse> listProfiles(@PathVariable UUID workspaceId) {
        return agentService.listProfiles(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/agents")
    public ResponseEntity<AgentProfileResponse> createProfile(
            @PathVariable UUID workspaceId,
            @RequestBody AgentProfileRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createProfile(workspaceId, request));
    }

    @PatchMapping("/agents/{profileId}")
    public AgentProfileResponse updateProfile(
            @PathVariable UUID profileId,
            @RequestBody AgentProfileRequest request
    ) {
        return agentService.updateProfile(profileId, request);
    }
}
