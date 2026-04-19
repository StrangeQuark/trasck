package com.strangequark.trasck.agent;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentWorkerController {

    public static final String WORKER_TOKEN_HEADER = "X-Trasck-Worker-Token";

    private final AgentService agentService;

    public AgentWorkerController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/claim")
    public ResponseEntity<AgentWorkerTaskResponse> claimTask(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody(required = false) AgentWorkerClaimRequest request
    ) {
        return agentService.claimWorkerTask(workspaceId, providerKey, workerToken, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/{taskId}/heartbeat")
    public AgentTaskResponse heartbeat(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @PathVariable UUID taskId,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody AgentWorkerHeartbeatRequest request
    ) {
        return agentService.workerHeartbeat(workspaceId, providerKey, taskId, workerToken, request);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/{taskId}/logs")
    public AgentTaskResponse log(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @PathVariable UUID taskId,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody AgentWorkerEventRequest request
    ) {
        return agentService.workerLog(workspaceId, providerKey, taskId, workerToken, request);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/{taskId}/messages")
    public AgentTaskResponse message(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @PathVariable UUID taskId,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody AgentTaskCallbackMessageRequest request
    ) {
        return agentService.workerMessage(workspaceId, providerKey, taskId, workerToken, request);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/{taskId}/artifacts")
    public AgentTaskResponse artifact(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @PathVariable UUID taskId,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody AgentTaskCallbackArtifactRequest request
    ) {
        return agentService.workerArtifact(workspaceId, providerKey, taskId, workerToken, request);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/{taskId}/cancel")
    public AgentTaskResponse cancel(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @PathVariable UUID taskId,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody(required = false) AgentWorkerEventRequest request
    ) {
        return agentService.workerCancel(workspaceId, providerKey, taskId, workerToken, request);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-workers/{providerKey}/tasks/{taskId}/retry")
    public AgentWorkerTaskResponse retry(
            @PathVariable UUID workspaceId,
            @PathVariable String providerKey,
            @PathVariable UUID taskId,
            @RequestHeader(WORKER_TOKEN_HEADER) String workerToken,
            @RequestBody(required = false) AgentWorkerEventRequest request
    ) {
        return agentService.workerRetry(workspaceId, providerKey, taskId, workerToken, request);
    }
}
