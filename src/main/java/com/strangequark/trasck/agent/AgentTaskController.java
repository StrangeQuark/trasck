package com.strangequark.trasck.agent;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentTaskController {

    private final AgentService agentService;

    public AgentTaskController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/work-items/{workItemId}/assign-agent")
    public ResponseEntity<AgentTaskResponse> assignAgent(
            @PathVariable UUID workItemId,
            @RequestBody AgentTaskAssignRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.assignAgent(workItemId, request));
    }

    @GetMapping("/agent-tasks/{taskId}")
    public AgentTaskResponse getTask(@PathVariable UUID taskId) {
        return agentService.getTask(taskId);
    }

    @PostMapping("/agent-tasks/{taskId}/cancel")
    public AgentTaskResponse cancelTask(@PathVariable UUID taskId) {
        return agentService.cancelTask(taskId);
    }

    @PostMapping("/agent-tasks/{taskId}/retry")
    public AgentTaskResponse retryTask(@PathVariable UUID taskId) {
        return agentService.retryTask(taskId);
    }

    @PostMapping("/agent-tasks/{taskId}/accept-result")
    public AgentTaskResponse acceptResult(@PathVariable UUID taskId) {
        return agentService.acceptResult(taskId);
    }

    @PostMapping("/agent-tasks/{taskId}/messages")
    public AgentTaskResponse addHumanMessage(
            @PathVariable UUID taskId,
            @RequestBody AgentTaskHumanMessageRequest request
    ) {
        return agentService.addHumanMessage(taskId, request);
    }

    @PostMapping("/agent-tasks/{taskId}/request-changes")
    public AgentTaskResponse requestChanges(
            @PathVariable UUID taskId,
            @RequestBody AgentTaskRequestChangesRequest request
    ) {
        return agentService.requestChanges(taskId, request);
    }

    @PostMapping("/agent-tasks/{taskId}/worker-dispatch")
    public AgentWorkerTaskResponse workerDispatch(@PathVariable UUID taskId) {
        return agentService.workerDispatch(taskId);
    }

    @PostMapping("/agent-callbacks/{providerKey}")
    public AgentTaskResponse callback(
            @PathVariable String providerKey,
            @RequestHeader(AgentCallbackJwtService.CALLBACK_HEADER) String assertion,
            @RequestBody AgentTaskCallbackRequest request
    ) {
        return agentService.handleCallback(providerKey, assertion, request);
    }
}
