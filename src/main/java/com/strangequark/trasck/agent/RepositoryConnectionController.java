package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RepositoryConnectionController {

    private final AgentService agentService;

    public RepositoryConnectionController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/workspaces/{workspaceId}/repository-connections")
    public List<RepositoryConnectionResponse> listRepositoryConnections(@PathVariable UUID workspaceId) {
        return agentService.listRepositoryConnections(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/repository-connections")
    public ResponseEntity<RepositoryConnectionResponse> createRepositoryConnection(
            @PathVariable UUID workspaceId,
            @RequestBody RepositoryConnectionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createRepositoryConnection(workspaceId, request));
    }
}
