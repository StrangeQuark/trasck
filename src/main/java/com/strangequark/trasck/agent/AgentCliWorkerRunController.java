package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentCliWorkerRunController {

    private final AgentService agentService;

    public AgentCliWorkerRunController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/workspaces/{workspaceId}/agent-cli-runs")
    public List<AgentCliWorkerRunResponse> listRuns(@PathVariable UUID workspaceId) {
        return agentService.listCliWorkerRuns(workspaceId);
    }

    @GetMapping("/workspaces/{workspaceId}/agent-cli-runs/{agentTaskId}/download")
    public ResponseEntity<byte[]> downloadRun(@PathVariable UUID workspaceId, @PathVariable UUID agentTaskId) {
        AgentCliWorkerRunArchive archive = agentService.archiveCliWorkerRun(workspaceId, agentTaskId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(archive.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archive.filename()).build().toString())
                .body(archive.bytes());
    }

    @DeleteMapping("/workspaces/{workspaceId}/agent-cli-runs/{agentTaskId}")
    public AgentCliWorkerRunDeleteResponse deleteRun(@PathVariable UUID workspaceId, @PathVariable UUID agentTaskId) {
        return agentService.deleteCliWorkerRun(workspaceId, agentTaskId);
    }

    @PostMapping("/workspaces/{workspaceId}/agent-cli-runs/prune")
    public ResponseEntity<AgentCliWorkerRunDeleteResponse> pruneRuns(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) AgentCliWorkerRunPruneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(agentService.pruneCliWorkerRuns(workspaceId, request));
    }
}
