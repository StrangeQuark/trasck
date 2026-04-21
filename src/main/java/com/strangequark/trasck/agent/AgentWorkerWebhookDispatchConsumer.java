package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.ConfiguredDomainEventConsumer;
import com.strangequark.trasck.event.DomainEvent;
import com.strangequark.trasck.event.EventConsumerConfig;
import com.strangequark.trasck.security.OutboundUrlPolicy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgentWorkerWebhookDispatchConsumer implements ConfiguredDomainEventConsumer {

    private static final String EVENT_TYPE = "agent.worker.dispatch_requested";

    private final ObjectMapper objectMapper;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentProviderRepository agentProviderRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository;
    private final AgentTaskEventRepository agentTaskEventRepository;
    private final AgentCallbackJwtService callbackJwtService;
    private final OutboundUrlPolicy outboundUrlPolicy;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public AgentWorkerWebhookDispatchConsumer(
            ObjectMapper objectMapper,
            AgentTaskRepository agentTaskRepository,
            AgentProviderRepository agentProviderRepository,
            AgentProfileRepository agentProfileRepository,
            AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository,
            AgentTaskEventRepository agentTaskEventRepository,
            AgentCallbackJwtService callbackJwtService,
            OutboundUrlPolicy outboundUrlPolicy
    ) {
        this.objectMapper = objectMapper;
        this.agentTaskRepository = agentTaskRepository;
        this.agentProviderRepository = agentProviderRepository;
        this.agentProfileRepository = agentProfileRepository;
        this.agentTaskRepositoryLinkRepository = agentTaskRepositoryLinkRepository;
        this.agentTaskEventRepository = agentTaskEventRepository;
        this.callbackJwtService = callbackJwtService;
        this.outboundUrlPolicy = outboundUrlPolicy;
    }

    @Override
    public String consumerType() {
        return "agent_worker_webhook";
    }

    @Override
    public void handle(DomainEvent event, EventConsumerConfig config) {
        if (!EVENT_TYPE.equals(event.getEventType())) {
            return;
        }
        JsonNode payload = event.getPayload();
        UUID providerId = requiredUuid(payload, "providerId");
        if (!configuredProviderMatches(config, providerId)) {
            return;
        }
        UUID taskId = requiredUuid(payload, "agentTaskId");
        UUID profileId = requiredUuid(payload, "agentProfileId");
        AgentTask task = agentTaskRepository.findByIdAndWorkspaceId(taskId, event.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Agent task not found for worker webhook dispatch"));
        AgentProvider provider = agentProviderRepository.findByIdAndWorkspaceId(providerId, event.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Agent provider not found for worker webhook dispatch"));
        AgentProfile profile = agentProfileRepository.findByIdAndWorkspaceId(profileId, event.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Agent profile not found for worker webhook dispatch"));
        String callbackUrl = callbackUrl(config, provider);
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalStateException("Worker webhook callback URL is not configured");
        }

        AgentWorkerTaskResponse dispatch = AgentWorkerTaskResponse.from(
                task,
                provider,
                agentTaskRepositoryLinkRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId()),
                "webhook_push",
                workerEndpoints(task, provider),
                callbackJwtService.issue(task, provider, profile)
        );
        postDispatch(event, task, callbackUrl, dispatch);
    }

    private void postDispatch(DomainEvent event, AgentTask task, String callbackUrl, AgentWorkerTaskResponse dispatch) {
        try {
            URI callbackUri = URI.create(callbackUrl);
            outboundUrlPolicy.validateResolvedHttpUri(callbackUri, "callbackUrl");
            HttpRequest request = HttpRequest.newBuilder(callbackUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Trasck-Domain-Event-Id", event.getId().toString())
                    .header("X-Trasck-Agent-Task-Id", task.getId().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(dispatch)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                appendTaskEvent(task, "worker_webhook_delivered", "info", "Worker webhook dispatch delivered.", objectMapper.createObjectNode()
                        .put("domainEventId", event.getId().toString())
                        .put("responseStatus", response.statusCode()));
                return;
            }
            appendTaskEvent(task, "worker_webhook_delivery_failed", "error", "Worker webhook dispatch failed.", objectMapper.createObjectNode()
                    .put("domainEventId", event.getId().toString())
                    .put("responseStatus", response.statusCode()));
            throw new IllegalStateException("Worker webhook delivery failed with HTTP " + response.statusCode());
        } catch (IOException ex) {
            appendTaskEvent(task, "worker_webhook_delivery_failed", "error", "Worker webhook dispatch failed.", errorMetadata(event, ex));
            throw new IllegalStateException("Worker webhook delivery failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            appendTaskEvent(task, "worker_webhook_delivery_failed", "error", "Worker webhook dispatch interrupted.", errorMetadata(event, ex));
            throw new IllegalStateException("Worker webhook delivery interrupted", ex);
        }
    }

    private void appendTaskEvent(AgentTask task, String eventType, String severity, String message, JsonNode metadata) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setAgentTaskId(task.getId());
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setMessage(message);
        event.setMetadata(metadata);
        agentTaskEventRepository.save(event);
    }

    private ObjectNode errorMetadata(DomainEvent event, Exception ex) {
        return objectMapper.createObjectNode()
                .put("domainEventId", event.getId().toString())
                .put("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    private Map<String, String> workerEndpoints(AgentTask task, AgentProvider provider) {
        String base = "/api/v1/workspaces/" + task.getWorkspaceId() + "/agent-workers/" + provider.getProviderKey() + "/tasks/" + task.getId();
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("heartbeat", base + "/heartbeat");
        endpoints.put("cancel", base + "/cancel");
        endpoints.put("retry", base + "/retry");
        endpoints.put("logs", base + "/logs");
        endpoints.put("messages", base + "/messages");
        endpoints.put("artifacts", base + "/artifacts");
        endpoints.put("callback", "/api/v1/agent-callbacks/" + provider.getProviderKey());
        return endpoints;
    }

    private boolean configuredProviderMatches(EventConsumerConfig config, UUID providerId) {
        JsonNode configuredProviderId = config.getConfig() == null ? null : config.getConfig().path("providerId");
        return configuredProviderId == null || configuredProviderId.isMissingNode() || providerId.toString().equals(configuredProviderId.asText());
    }

    private String callbackUrl(EventConsumerConfig config, AgentProvider provider) {
        if (config.getConfig() != null && config.getConfig().hasNonNull("callbackUrl")) {
            return config.getConfig().path("callbackUrl").asText();
        }
        return provider.getCallbackUrl();
    }

    private UUID requiredUuid(JsonNode payload, String fieldName) {
        if (payload == null || !payload.hasNonNull(fieldName)) {
            throw new IllegalStateException("Worker dispatch event is missing " + fieldName);
        }
        return UUID.fromString(payload.path(fieldName).asText());
    }
}
