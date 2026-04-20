package com.strangequark.trasck.agent;

import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.integration.ExportJobResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentDispatchAttemptController {

    private final AgentService agentService;

    public AgentDispatchAttemptController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/workspaces/{workspaceId}/agent-dispatch-attempts")
    public CursorPageResponse<AgentDispatchAttemptResponse> listDispatchAttempts(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID agentTaskId,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) UUID agentProfileId,
            @RequestParam(required = false) UUID workItemId,
            @RequestParam(required = false) String attemptType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OffsetDateTime startedFrom,
            @RequestParam(required = false) OffsetDateTime startedTo,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return agentService.listDispatchAttempts(
                workspaceId,
                agentTaskId,
                providerId,
                agentProfileId,
                workItemId,
                attemptType,
                status,
                startedFrom,
                startedTo,
                limit,
                cursor
        );
    }

    @PostMapping("/workspaces/{workspaceId}/agent-dispatch-attempts/export")
    public ResponseEntity<ExportJobResponse> exportDispatchAttempts(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) AgentDispatchAttemptExportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.exportDispatchAttempts(workspaceId, request));
    }

    @PostMapping("/workspaces/{workspaceId}/agent-dispatch-attempts/prune")
    public AgentDispatchAttemptRetentionResponse pruneDispatchAttempts(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) AgentDispatchAttemptRetentionRequest request
    ) {
        return agentService.pruneDispatchAttempts(workspaceId, request);
    }
}
