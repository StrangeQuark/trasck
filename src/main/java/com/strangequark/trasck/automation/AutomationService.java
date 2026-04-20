package com.strangequark.trasck.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.integration.Webhook;
import com.strangequark.trasck.integration.WebhookDelivery;
import com.strangequark.trasck.integration.WebhookDeliveryRepository;
import com.strangequark.trasck.integration.WebhookDeliveryResponse;
import com.strangequark.trasck.integration.WebhookDeliveryWorkerRequest;
import com.strangequark.trasck.integration.WebhookDeliveryWorkerResponse;
import com.strangequark.trasck.integration.WebhookRepository;
import com.strangequark.trasck.integration.WebhookRequest;
import com.strangequark.trasck.integration.WebhookResponse;
import com.strangequark.trasck.notification.Notification;
import com.strangequark.trasck.notification.NotificationRepository;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutomationService {

    private final ObjectMapper objectMapper;
    private final AutomationRuleRepository automationRuleRepository;
    private final AutomationConditionRepository automationConditionRepository;
    private final AutomationActionRepository automationActionRepository;
    private final AutomationExecutionJobRepository automationExecutionJobRepository;
    private final AutomationExecutionLogRepository automationExecutionLogRepository;
    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final NotificationRepository notificationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public AutomationService(
            ObjectMapper objectMapper,
            AutomationRuleRepository automationRuleRepository,
            AutomationConditionRepository automationConditionRepository,
            AutomationActionRepository automationActionRepository,
            AutomationExecutionJobRepository automationExecutionJobRepository,
            AutomationExecutionLogRepository automationExecutionLogRepository,
            WebhookRepository webhookRepository,
            WebhookDeliveryRepository webhookDeliveryRepository,
            NotificationRepository notificationRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.automationRuleRepository = automationRuleRepository;
        this.automationConditionRepository = automationConditionRepository;
        this.automationActionRepository = automationActionRepository;
        this.automationExecutionJobRepository = automationExecutionJobRepository;
        this.automationExecutionLogRepository = automationExecutionLogRepository;
        this.webhookRepository = webhookRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.notificationRepository = notificationRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<AutomationRuleResponse> listRules(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return automationRuleRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(this::ruleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AutomationRuleResponse getRule(UUID ruleId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        return ruleResponse(rule);
    }

    @Transactional
    public AutomationRuleResponse createRule(UUID workspaceId, AutomationRuleRequest request) {
        AutomationRuleRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        AutomationRule rule = new AutomationRule();
        rule.setWorkspaceId(workspaceId);
        applyRuleRequest(rule, createRequest, true);
        OffsetDateTime now = OffsetDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        AutomationRule saved = automationRuleRepository.save(rule);
        recordRuleEvent(saved, "automation.rule_created", actorId);
        return ruleResponse(saved);
    }

    @Transactional
    public AutomationRuleResponse updateRule(UUID ruleId, AutomationRuleRequest request) {
        AutomationRuleRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        applyRuleRequest(rule, updateRequest, false);
        rule.setUpdatedAt(OffsetDateTime.now());
        AutomationRule saved = automationRuleRepository.save(rule);
        recordRuleEvent(saved, "automation.rule_updated", actorId);
        return ruleResponse(saved);
    }

    @Transactional
    public void deleteRule(UUID ruleId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        automationRuleRepository.delete(rule);
        recordRuleEvent(rule, "automation.rule_deleted", actorId);
    }

    @Transactional
    public AutomationConditionResponse createCondition(UUID ruleId, AutomationConditionRequest request) {
        AutomationConditionRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        AutomationCondition condition = new AutomationCondition();
        condition.setRuleId(rule.getId());
        applyConditionRequest(condition, createRequest, true);
        AutomationCondition saved = automationConditionRepository.save(condition);
        recordRuleEvent(rule, "automation.condition_created", actorId);
        return AutomationConditionResponse.from(saved);
    }

    @Transactional
    public AutomationConditionResponse updateCondition(UUID ruleId, UUID conditionId, AutomationConditionRequest request) {
        AutomationConditionRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        AutomationCondition condition = automationConditionRepository.findByIdAndRuleId(conditionId, rule.getId())
                .orElseThrow(() -> notFound("Automation condition not found"));
        applyConditionRequest(condition, updateRequest, false);
        AutomationCondition saved = automationConditionRepository.save(condition);
        recordRuleEvent(rule, "automation.condition_updated", actorId);
        return AutomationConditionResponse.from(saved);
    }

    @Transactional
    public void deleteCondition(UUID ruleId, UUID conditionId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        AutomationCondition condition = automationConditionRepository.findByIdAndRuleId(conditionId, rule.getId())
                .orElseThrow(() -> notFound("Automation condition not found"));
        automationConditionRepository.delete(condition);
        recordRuleEvent(rule, "automation.condition_deleted", actorId);
    }

    @Transactional
    public AutomationActionResponse createAction(UUID ruleId, AutomationActionRequest request) {
        AutomationActionRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        AutomationAction action = new AutomationAction();
        action.setRuleId(rule.getId());
        applyActionRequest(action, createRequest, true);
        AutomationAction saved = automationActionRepository.save(action);
        recordRuleEvent(rule, "automation.action_created", actorId);
        return AutomationActionResponse.from(saved);
    }

    @Transactional
    public AutomationActionResponse updateAction(UUID ruleId, UUID actionId, AutomationActionRequest request) {
        AutomationActionRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        AutomationAction action = automationActionRepository.findByIdAndRuleId(actionId, rule.getId())
                .orElseThrow(() -> notFound("Automation action not found"));
        applyActionRequest(action, updateRequest, false);
        AutomationAction saved = automationActionRepository.save(action);
        recordRuleEvent(rule, "automation.action_updated", actorId);
        return AutomationActionResponse.from(saved);
    }

    @Transactional
    public void deleteAction(UUID ruleId, UUID actionId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        AutomationAction action = automationActionRepository.findByIdAndRuleId(actionId, rule.getId())
                .orElseThrow(() -> notFound("Automation action not found"));
        automationActionRepository.delete(action);
        recordRuleEvent(rule, "automation.action_deleted", actorId);
    }

    @Transactional
    public AutomationExecutionJobResponse executeRule(UUID ruleId, AutomationExecutionRequest request) {
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            throw badRequest("Automation rule is disabled");
        }
        AutomationExecutionRequest executionRequest = request == null
                ? new AutomationExecutionRequest("manual", null, objectMapper.createObjectNode())
                : request;
        AutomationExecutionJob job = new AutomationExecutionJob();
        job.setRuleId(rule.getId());
        job.setWorkspaceId(rule.getWorkspaceId());
        job.setSourceEntityType(hasText(executionRequest.sourceEntityType()) ? executionRequest.sourceEntityType().trim() : "manual");
        job.setSourceEntityId(executionRequest.sourceEntityId());
        job.setStatus("queued");
        job.setPayload(toJsonObject(executionRequest.payload()));
        job.setAttempts(0);
        job.setCreatedAt(OffsetDateTime.now());
        AutomationExecutionJob saved = automationExecutionJobRepository.save(job);
        recordRuleEvent(rule, "automation.rule_queued", actorId);
        return jobResponse(saved);
    }

    @Transactional
    public AutomationExecutionJobResponse runJob(UUID jobId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationExecutionJob job = automationExecutionJobRepository.findById(jobId).orElseThrow(() -> notFound("Automation execution job not found"));
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "automation.admin");
        if (!List.of("queued", "failed").contains(job.getStatus())) {
            throw badRequest("Only queued or failed automation jobs can be run");
        }
        AutomationRule rule = rule(job.getRuleId());
        processJob(rule, job, actorId);
        return jobResponse(job);
    }

    @Transactional
    public AutomationWorkerRunResponse runQueuedJobs(UUID workspaceId, Integer limit) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        int pageLimit = normalizeWorkerLimit(limit);
        List<AutomationExecutionJob> jobs = automationExecutionJobRepository.findProcessableJobs(workspaceId, OffsetDateTime.now(), pageLimit);
        List<AutomationExecutionJobResponse> responses = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;
        for (AutomationExecutionJob job : jobs) {
            AutomationRule rule = rule(job.getRuleId());
            processJob(rule, job, actorId);
            if ("succeeded".equals(job.getStatus())) {
                succeeded++;
            } else if ("failed".equals(job.getStatus())) {
                failed++;
            }
            responses.add(jobResponse(job));
        }
        return new AutomationWorkerRunResponse(workspaceId, responses.size(), succeeded, failed, responses);
    }

    @Transactional(readOnly = true)
    public List<AutomationExecutionJobResponse> listJobs(UUID ruleId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationRule rule = rule(ruleId);
        permissionService.requireWorkspacePermission(actorId, rule.getWorkspaceId(), "automation.admin");
        return automationExecutionJobRepository.findByRuleIdOrderByCreatedAtDesc(rule.getId()).stream()
                .map(this::jobResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AutomationExecutionJobResponse getJob(UUID jobId) {
        UUID actorId = currentUserService.requireUserId();
        AutomationExecutionJob job = automationExecutionJobRepository.findById(jobId).orElseThrow(() -> notFound("Automation execution job not found"));
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "automation.admin");
        return jobResponse(job);
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> listWebhooks(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return webhookRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(WebhookResponse::from)
                .toList();
    }

    @Transactional
    public WebhookResponse createWebhook(UUID workspaceId, WebhookRequest request) {
        WebhookRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        Webhook webhook = new Webhook();
        webhook.setWorkspaceId(workspaceId);
        applyWebhookRequest(webhook, createRequest, true);
        Webhook saved = webhookRepository.save(webhook);
        recordWebhookEvent(saved, "webhook.created", actorId);
        return WebhookResponse.from(saved);
    }

    @Transactional
    public WebhookResponse updateWebhook(UUID webhookId, WebhookRequest request) {
        WebhookRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Webhook webhook = webhook(webhookId);
        permissionService.requireWorkspacePermission(actorId, webhook.getWorkspaceId(), "automation.admin");
        applyWebhookRequest(webhook, updateRequest, false);
        Webhook saved = webhookRepository.save(webhook);
        recordWebhookEvent(saved, "webhook.updated", actorId);
        return WebhookResponse.from(saved);
    }

    @Transactional
    public void deleteWebhook(UUID webhookId) {
        UUID actorId = currentUserService.requireUserId();
        Webhook webhook = webhook(webhookId);
        permissionService.requireWorkspacePermission(actorId, webhook.getWorkspaceId(), "automation.admin");
        webhook.setEnabled(false);
        webhookRepository.save(webhook);
        recordWebhookEvent(webhook, "webhook.disabled", actorId);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> listWebhookDeliveries(UUID webhookId) {
        UUID actorId = currentUserService.requireUserId();
        Webhook webhook = webhook(webhookId);
        permissionService.requireWorkspacePermission(actorId, webhook.getWorkspaceId(), "automation.admin");
        return webhookDeliveryRepository.findByWebhookIdOrderByCreatedAtDesc(webhook.getId()).stream()
                .map(WebhookDeliveryResponse::from)
                .toList();
    }

    @Transactional
    public WebhookDeliveryWorkerResponse processWebhookDeliveries(UUID workspaceId, WebhookDeliveryWorkerRequest request) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        int limit = normalizeWorkerLimit(request == null ? null : request.limit());
        int maxAttempts = normalizeMaxAttempts(request == null ? null : request.maxAttempts());
        boolean dryRun = request == null || !Boolean.FALSE.equals(request.dryRun());
        List<WebhookDelivery> deliveries = webhookDeliveryRepository.findProcessableDeliveries(workspaceId, OffsetDateTime.now(), limit);
        List<WebhookDeliveryResponse> responses = new ArrayList<>();
        int delivered = 0;
        int failed = 0;
        int deadLettered = 0;
        for (WebhookDelivery delivery : deliveries) {
            processWebhookDelivery(delivery, maxAttempts, dryRun);
            if ("delivered".equals(delivery.getStatus())) {
                delivered++;
            } else if ("dead_letter".equals(delivery.getStatus())) {
                deadLettered++;
            } else {
                failed++;
            }
            responses.add(WebhookDeliveryResponse.from(delivery));
        }
        return new WebhookDeliveryWorkerResponse(workspaceId, responses.size(), delivered, failed, deadLettered, responses);
    }

    private void processJob(AutomationRule rule, AutomationExecutionJob job, UUID actorId) {
        job.setStatus("running");
        job.setAttempts(job.getAttempts() == null ? 1 : job.getAttempts() + 1);
        job.setStartedAt(OffsetDateTime.now());
        automationExecutionJobRepository.save(job);
        try {
            List<AutomationAction> actions = automationActionRepository.findByRuleIdOrderByPositionAsc(rule.getId());
            for (AutomationAction action : actions) {
                runAction(rule, job, action, actorId);
            }
            job.setStatus("succeeded");
            job.setCompletedAt(OffsetDateTime.now());
            job.setFailedAt(null);
            job.setLastError(null);
            job.setNextAttemptAt(null);
            automationExecutionJobRepository.save(job);
        } catch (ResponseStatusException ex) {
            job.setStatus("failed");
            job.setFailedAt(OffsetDateTime.now());
            job.setLastError(ex.getReason());
            job.setNextAttemptAt(OffsetDateTime.now().plusMinutes(5));
            automationExecutionJobRepository.save(job);
        } catch (RuntimeException ex) {
            job.setStatus("failed");
            job.setFailedAt(OffsetDateTime.now());
            job.setLastError(ex.getMessage());
            job.setNextAttemptAt(OffsetDateTime.now().plusMinutes(5));
            automationExecutionJobRepository.save(job);
        }
    }

    private void runAction(AutomationRule rule, AutomationExecutionJob job, AutomationAction action, UUID actorId) {
        switch (action.getActionType()) {
            case "create_notification", "notification" -> runNotificationAction(rule, job, action, actorId);
            case "email" -> log(job, action, "queued", "Email delivery queued for worker dispatch", action.getConfig());
            case "webhook" -> runWebhookAction(rule, job, action);
            default -> log(job, action, "skipped", "No executable runner is registered for action type " + action.getActionType(), action.getConfig());
        }
    }

    private void processWebhookDelivery(WebhookDelivery delivery, int maxAttempts, boolean dryRun) {
        Webhook webhook = webhook(delivery.getWebhookId());
        int attempt = delivery.getAttemptCount() == null ? 1 : delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempt);
        if (dryRun) {
            delivery.setStatus("delivered");
            delivery.setResponseCode(202);
            delivery.setResponseBody("Dry-run delivery accepted by Trasck worker");
            delivery.setNextRetryAt(null);
            webhookDeliveryRepository.save(delivery);
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Trasck-Event-Type", delivery.getEventType())
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload() == null ? "{}" : delivery.getPayload().toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setResponseCode(response.statusCode());
            delivery.setResponseBody(truncate(response.body(), 4000));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("delivered");
                delivery.setNextRetryAt(null);
            } else {
                markWebhookDeliveryFailed(delivery, attempt, maxAttempts, "HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            markWebhookDeliveryFailed(delivery, attempt, maxAttempts, ex.getMessage());
        }
        webhookDeliveryRepository.save(delivery);
    }

    private void markWebhookDeliveryFailed(WebhookDelivery delivery, int attempt, int maxAttempts, String message) {
        delivery.setStatus(attempt >= maxAttempts ? "dead_letter" : "failed");
        delivery.setResponseBody(truncate(message, 4000));
        delivery.setNextRetryAt(attempt >= maxAttempts ? null : OffsetDateTime.now().plusMinutes(Math.min(60, attempt * 5L)));
    }

    private void runNotificationAction(AutomationRule rule, AutomationExecutionJob job, AutomationAction action, UUID actorId) {
        JsonNode config = jsonObject(action.getConfig());
        UUID userId = uuidFromConfig(config, "userId");
        if (!workspaceMembershipRepository.existsByWorkspaceIdAndUserIdAndStatusIgnoreCase(rule.getWorkspaceId(), userId, "active")) {
            throw badRequest("Notification action userId must be an active workspace member");
        }
        Notification notification = new Notification();
        notification.setWorkspaceId(rule.getWorkspaceId());
        notification.setUserId(userId);
        notification.setActorId(actorId);
        notification.setType(text(config, "type", "automation"));
        notification.setTitle(requiredText(text(config, "title", null), "config.title"));
        notification.setBody(text(config, "body", null));
        notification.setTargetType(text(config, "targetType", job.getSourceEntityType()));
        notification.setTargetId(config.hasNonNull("targetId") ? uuidFromConfig(config, "targetId") : job.getSourceEntityId());
        notification.setCreatedAt(OffsetDateTime.now());
        Notification saved = notificationRepository.save(notification);
        ObjectNode metadata = objectMapper.createObjectNode().put("notificationId", saved.getId().toString());
        log(job, action, "succeeded", "Notification created", metadata);
    }

    private void runWebhookAction(AutomationRule rule, AutomationExecutionJob job, AutomationAction action) {
        JsonNode config = jsonObject(action.getConfig());
        UUID webhookId = uuidFromConfig(config, "webhookId");
        Webhook webhook = webhookRepository.findByIdAndWorkspaceId(webhookId, rule.getWorkspaceId())
                .orElseThrow(() -> badRequest("Webhook action webhookId must reference a webhook in this workspace"));
        if (!Boolean.TRUE.equals(webhook.getEnabled())) {
            throw badRequest("Webhook is disabled");
        }
        ObjectNode payload = objectMapper.createObjectNode()
                .put("automationRuleId", rule.getId().toString())
                .put("automationJobId", job.getId().toString())
                .put("actionId", action.getId().toString())
                .put("eventType", text(config, "eventType", rule.getTriggerType()));
        if (job.getPayload() != null) {
            payload.set("payload", job.getPayload());
        }
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookId(webhook.getId());
        delivery.setEventType(text(config, "eventType", rule.getTriggerType()));
        delivery.setPayload(payload);
        delivery.setStatus("queued");
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(OffsetDateTime.now());
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);
        ObjectNode metadata = objectMapper.createObjectNode().put("webhookDeliveryId", saved.getId().toString());
        log(job, action, "queued", "Webhook delivery queued", metadata);
    }

    private void applyRuleRequest(AutomationRule rule, AutomationRuleRequest request, boolean create) {
        if (create || request.projectId() != null) {
            rule.setProjectId(request.projectId() == null ? null : validateProject(rule.getWorkspaceId(), request.projectId()).getId());
        }
        if (create || hasText(request.name())) {
            rule.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.triggerType())) {
            rule.setTriggerType(requiredText(request.triggerType(), "triggerType").toLowerCase());
        }
        if (create || request.triggerConfig() != null) {
            rule.setTriggerConfig(toJsonObject(request.triggerConfig()));
        }
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        } else if (create) {
            rule.setEnabled(true);
        }
    }

    private void applyConditionRequest(AutomationCondition condition, AutomationConditionRequest request, boolean create) {
        if (create || hasText(request.conditionType())) {
            condition.setConditionType(requiredText(request.conditionType(), "conditionType").toLowerCase());
        }
        if (create || request.config() != null) {
            condition.setConfig(toJsonObject(request.config()));
        }
        if (request.position() != null) {
            condition.setPosition(nonNegative(request.position(), "position"));
        } else if (create) {
            condition.setPosition(0);
        }
    }

    private void applyActionRequest(AutomationAction action, AutomationActionRequest request, boolean create) {
        if (create || hasText(request.actionType())) {
            action.setActionType(requiredText(request.actionType(), "actionType").toLowerCase());
        }
        if (create || hasText(request.executionMode())) {
            action.setExecutionMode(normalizeExecutionMode(request.executionMode()));
        }
        if (create || request.config() != null) {
            action.setConfig(toJsonObject(request.config()));
        }
        if (request.position() != null) {
            action.setPosition(nonNegative(request.position(), "position"));
        } else if (create) {
            action.setPosition(0);
        }
    }

    private void applyWebhookRequest(Webhook webhook, WebhookRequest request, boolean create) {
        if (create || hasText(request.name())) {
            webhook.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.url())) {
            String url = requiredText(request.url(), "url");
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException ex) {
                throw badRequest("url must be a valid URI");
            }
            if (!List.of("http", "https").contains(uri.getScheme())) {
                throw badRequest("url must use http or https");
            }
            webhook.setUrl(url);
        }
        if (hasText(request.secret())) {
            webhook.setSecretHash(sha256(request.secret()));
        }
        if (create || request.eventTypes() != null) {
            webhook.setEventTypes(toJsonArray(request.eventTypes(), "eventTypes"));
        }
        if (request.enabled() != null) {
            webhook.setEnabled(request.enabled());
        } else if (create) {
            webhook.setEnabled(true);
        }
    }

    private AutomationRuleResponse ruleResponse(AutomationRule rule) {
        return AutomationRuleResponse.from(
                rule,
                automationConditionRepository.findByRuleIdOrderByPositionAsc(rule.getId()),
                automationActionRepository.findByRuleIdOrderByPositionAsc(rule.getId())
        );
    }

    private AutomationExecutionJobResponse jobResponse(AutomationExecutionJob job) {
        return AutomationExecutionJobResponse.from(job, automationExecutionLogRepository.findByJobIdOrderByCreatedAtAsc(job.getId()));
    }

    private AutomationRule rule(UUID ruleId) {
        return automationRuleRepository.findById(ruleId).orElseThrow(() -> notFound("Automation rule not found"));
    }

    private Webhook webhook(UUID webhookId) {
        return webhookRepository.findById(webhookId).orElseThrow(() -> notFound("Webhook not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project validateProject(UUID workspaceId, UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> badRequest("Project not found in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("Project not found in this workspace");
        }
        return project;
    }

    private void log(AutomationExecutionJob job, AutomationAction action, String status, String message, JsonNode metadata) {
        AutomationExecutionLog log = new AutomationExecutionLog();
        log.setJobId(job.getId());
        log.setActionId(action.getId());
        log.setStatus(status);
        log.setMessage(message);
        log.setMetadata(metadata == null ? objectMapper.createObjectNode() : metadata);
        log.setCreatedAt(OffsetDateTime.now());
        automationExecutionLogRepository.save(log);
    }

    private void recordRuleEvent(AutomationRule rule, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("automationRuleId", rule.getId().toString())
                .put("automationRuleName", rule.getName())
                .put("actorUserId", actorId.toString());
        domainEventService.record(rule.getWorkspaceId(), "automation_rule", rule.getId(), eventType, payload);
    }

    private void recordWebhookEvent(Webhook webhook, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("webhookId", webhook.getId().toString())
                .put("webhookName", webhook.getName())
                .put("actorUserId", actorId.toString());
        domainEventService.record(webhook.getWorkspaceId(), "webhook", webhook.getId(), eventType, payload);
    }

    private JsonNode toJsonNullable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private JsonNode toJsonObject(Object value) {
        JsonNode json = toJsonNullable(value);
        if (json == null || json.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!json.isObject()) {
            throw badRequest("JSON value must be an object");
        }
        return json;
    }

    private JsonNode toJsonArray(Object value, String fieldName) {
        JsonNode json = toJsonNullable(value);
        if (json == null || json.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (!json.isArray()) {
            throw badRequest(fieldName + " must be a JSON array");
        }
        return json;
    }

    private JsonNode jsonObject(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw badRequest("action config must be a JSON object");
        }
        return value;
    }

    private UUID uuidFromConfig(JsonNode config, String fieldName) {
        String value = text(config, fieldName, null);
        if (!hasText(value)) {
            throw badRequest("config." + fieldName + " is required");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw badRequest("config." + fieldName + " must be a UUID");
        }
    }

    private String text(JsonNode config, String fieldName, String fallback) {
        return config != null && config.hasNonNull(fieldName) ? config.get(fieldName).asText() : fallback;
    }

    private String normalizeExecutionMode(String executionMode) {
        String normalized = hasText(executionMode) ? executionMode.trim().toLowerCase() : "sync";
        if (!List.of("sync", "async", "hybrid").contains(normalized)) {
            throw badRequest("executionMode must be sync, async, or hybrid");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash webhook secret", ex);
        }
    }

    private int nonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw badRequest(fieldName + " must be zero or greater");
        }
        return value;
    }

    private int normalizeWorkerLimit(Integer limit) {
        if (limit == null) {
            return 25;
        }
        if (limit < 1 || limit > 100) {
            throw badRequest("limit must be between 1 and 100");
        }
        return limit;
    }

    private int normalizeMaxAttempts(Integer maxAttempts) {
        if (maxAttempts == null) {
            return 3;
        }
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw badRequest("maxAttempts must be between 1 and 20");
        }
        return maxAttempts;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw badRequest(fieldName + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
