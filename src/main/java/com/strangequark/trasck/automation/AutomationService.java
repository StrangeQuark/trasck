package com.strangequark.trasck.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.activity.Attachment;
import com.strangequark.trasck.activity.AttachmentRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.activity.storage.AttachmentStorageService;
import com.strangequark.trasck.activity.storage.AttachmentUpload;
import com.strangequark.trasck.activity.storage.StoredAttachment;
import com.strangequark.trasck.agent.SecretCipherService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import com.strangequark.trasck.integration.EmailDelivery;
import com.strangequark.trasck.integration.EmailDeliveryRepository;
import com.strangequark.trasck.integration.EmailDeliveryResponse;
import com.strangequark.trasck.integration.EmailDeliveryWorkerRequest;
import com.strangequark.trasck.integration.EmailDeliveryWorkerResponse;
import com.strangequark.trasck.integration.EmailProviderSettings;
import com.strangequark.trasck.integration.EmailProviderSettingsRepository;
import com.strangequark.trasck.integration.EmailProviderSettingsRequest;
import com.strangequark.trasck.integration.EmailProviderSettingsResponse;
import com.strangequark.trasck.integration.ExportJob;
import com.strangequark.trasck.integration.ExportJobRepository;
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
import com.strangequark.trasck.security.ContentLimitPolicy;
import com.strangequark.trasck.security.OutboundUrlPolicy;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AutomationService {

    private static final int MAX_WORKER_RUN_RETENTION_EXPORT_ROWS = 10_000;
    private static final DateTimeFormatter EXPORT_FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String WEBHOOK_SIGNATURE_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final AutomationRuleRepository automationRuleRepository;
    private final AutomationConditionRepository automationConditionRepository;
    private final AutomationActionRepository automationActionRepository;
    private final AutomationExecutionJobRepository automationExecutionJobRepository;
    private final AutomationExecutionLogRepository automationExecutionLogRepository;
    private final AutomationWorkerSettingsRepository automationWorkerSettingsRepository;
    private final AutomationWorkerRunRepository automationWorkerRunRepository;
    private final AutomationWorkerHealthRepository automationWorkerHealthRepository;
    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final EmailProviderSettingsRepository emailProviderSettingsRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final ExportJobRepository exportJobRepository;
    private final NotificationRepository notificationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;
    private final SecretCipherService secretCipherService;
    private final OutboundUrlPolicy outboundUrlPolicy;
    private final ContentLimitPolicy contentLimitPolicy;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Environment environment;
    private final String emailProvider;
    private final String defaultFromEmail;
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
            AutomationWorkerSettingsRepository automationWorkerSettingsRepository,
            AutomationWorkerRunRepository automationWorkerRunRepository,
            AutomationWorkerHealthRepository automationWorkerHealthRepository,
            WebhookRepository webhookRepository,
            WebhookDeliveryRepository webhookDeliveryRepository,
            EmailDeliveryRepository emailDeliveryRepository,
            EmailProviderSettingsRepository emailProviderSettingsRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            AttachmentRepository attachmentRepository,
            AttachmentStorageService attachmentStorageService,
            ExportJobRepository exportJobRepository,
            NotificationRepository notificationRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService,
            SecretCipherService secretCipherService,
            OutboundUrlPolicy outboundUrlPolicy,
            ContentLimitPolicy contentLimitPolicy,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Environment environment,
            @Value("${trasck.email.provider:maildev}") String emailProvider,
            @Value("${trasck.email.from:no-reply@trasck.local}") String defaultFromEmail
    ) {
        this.objectMapper = objectMapper;
        this.automationRuleRepository = automationRuleRepository;
        this.automationConditionRepository = automationConditionRepository;
        this.automationActionRepository = automationActionRepository;
        this.automationExecutionJobRepository = automationExecutionJobRepository;
        this.automationExecutionLogRepository = automationExecutionLogRepository;
        this.automationWorkerSettingsRepository = automationWorkerSettingsRepository;
        this.automationWorkerRunRepository = automationWorkerRunRepository;
        this.automationWorkerHealthRepository = automationWorkerHealthRepository;
        this.webhookRepository = webhookRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.emailProviderSettingsRepository = emailProviderSettingsRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.exportJobRepository = exportJobRepository;
        this.notificationRepository = notificationRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
        this.secretCipherService = secretCipherService;
        this.outboundUrlPolicy = outboundUrlPolicy;
        this.contentLimitPolicy = contentLimitPolicy;
        this.mailSenderProvider = mailSenderProvider;
        this.environment = environment;
        this.emailProvider = emailProvider;
        this.defaultFromEmail = defaultFromEmail;
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
        return runQueuedJobsInternal(workspaceId, limit, actorId);
    }

    @Transactional(readOnly = true)
    public AutomationWorkerSettingsResponse getWorkerSettings(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return AutomationWorkerSettingsResponse.from(workerSettings(workspaceId));
    }

    @Transactional
    public AutomationWorkerSettingsResponse updateWorkerSettings(UUID workspaceId, AutomationWorkerSettingsRequest request) {
        AutomationWorkerSettingsRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        AutomationWorkerSettings settings = workerSettings(workspaceId);
        applyWorkerSettings(settings, updateRequest);
        AutomationWorkerSettings saved = automationWorkerSettingsRepository.save(settings);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("actorUserId", actorId.toString());
        domainEventService.record(workspaceId, "automation_worker_settings", workspaceId, "automation_worker_settings.updated", payload);
        return AutomationWorkerSettingsResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AutomationWorkerRunHistoryResponse> listWorkerRuns(UUID workspaceId, String workerType) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        String normalizedWorkerType = normalizeWorkerTypeFilter(workerType);
        List<AutomationWorkerRun> runs = normalizedWorkerType == null
                ? automationWorkerRunRepository.findTop50ByWorkspaceIdOrderByStartedAtDesc(workspaceId)
                : automationWorkerRunRepository.findTop50ByWorkspaceIdAndWorkerTypeOrderByStartedAtDesc(workspaceId, normalizedWorkerType);
        return runs.stream()
                .map(AutomationWorkerRunHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AutomationWorkerHealthResponse> listWorkerHealth(UUID workspaceId, String workerType) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        String normalizedWorkerType = normalizeWorkerTypeFilter(workerType);
        List<AutomationWorkerHealth> healthRows = normalizedWorkerType == null
                ? automationWorkerHealthRepository.findByWorkspaceIdOrderByWorkerTypeAsc(workspaceId)
                : automationWorkerHealthRepository.findByWorkspaceIdAndWorkerTypeOrderByWorkerTypeAsc(workspaceId, normalizedWorkerType);
        return healthRows.stream()
                .map(AutomationWorkerHealthResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailProviderSettingsResponse getEmailProviderSettings(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return EmailProviderSettingsResponse.from(emailProviderSettingsRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> defaultEmailProviderSettings(workspaceId)));
    }

    @Transactional
    public EmailProviderSettingsResponse updateEmailProviderSettings(UUID workspaceId, EmailProviderSettingsRequest request) {
        EmailProviderSettingsRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        EmailProviderSettings settings = emailProviderSettingsRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> defaultEmailProviderSettings(workspaceId));
        applyEmailProviderSettings(settings, updateRequest);
        EmailProviderSettings saved = emailProviderSettingsRepository.save(settings);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("provider", saved.getProvider())
                .put("actorUserId", actorId.toString());
        domainEventService.record(workspaceId, "email_provider_settings", workspaceId, "email_provider_settings.updated", payload);
        return EmailProviderSettingsResponse.from(saved);
    }

    @Transactional
    public AutomationWorkerRunRetentionResponse exportWorkerRuns(
            UUID workspaceId,
            Integer limit,
            String workerType,
            String triggerType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo
    ) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        WorkerRunRetentionFilter filter = workerRunRetentionFilter(workerType, triggerType, status, startedFrom, startedTo);
        WorkerRunRetentionSnapshot snapshot = workerRunRetentionSnapshot(workspaceId, normalizeRetentionExportLimit(limit), filter);
        StoredWorkerRunRetentionExport export = storeWorkerRunRetentionExport(workspaceId, actorId, snapshot);
        return workerRunRetentionResponse(workspaceId, snapshot, export, 0);
    }

    @Transactional
    public AutomationWorkerRunRetentionResponse pruneWorkerRuns(
            UUID workspaceId,
            String workerType,
            String triggerType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo
    ) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return pruneWorkerRuns(workspaceId, actorId, workerRunRetentionFilter(workerType, triggerType, status, startedFrom, startedTo));
    }

    @Transactional
    public AutomationWorkerRunRetentionResponse pruneWorkerRunsInternal(UUID workspaceId) {
        activeWorkspace(workspaceId);
        return pruneWorkerRuns(workspaceId, null, WorkerRunRetentionFilter.empty());
    }

    @Transactional
    public AutomationWorkerRunRetentionResponse pruneWorkerRunsAutomatically(UUID workspaceId) {
        Workspace workspace = activeWorkspace(workspaceId);
        AutomationWorkerSettings settings = workerSettings(workspaceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!shouldRunAutomaticWorkerRunPruning(workspace, settings, now)) {
            return null;
        }
        settings.setWorkerRunPruningLastStartedAt(now);
        automationWorkerSettingsRepository.save(settings);
        AutomationWorkerRunRetentionResponse response = pruneWorkerRuns(workspaceId, null, WorkerRunRetentionFilter.empty());
        settings.setWorkerRunPruningLastFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        automationWorkerSettingsRepository.save(settings);
        return response;
    }

    private AutomationWorkerRunRetentionResponse pruneWorkerRuns(UUID workspaceId, UUID actorId, WorkerRunRetentionFilter filter) {
        WorkerRunRetentionSnapshot snapshot = workerRunRetentionSnapshot(workspaceId, MAX_WORKER_RUN_RETENTION_EXPORT_ROWS, filter);
        if (snapshot.runsEligible() > MAX_WORKER_RUN_RETENTION_EXPORT_ROWS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Too many automation worker runs are eligible for a single retention prune export"
            );
        }
        StoredWorkerRunRetentionExport export = snapshot.runsEligible() == 0
                ? null
                : Boolean.TRUE.equals(snapshot.settings().getWorkerRunExportBeforePrune())
                        ? storeWorkerRunRetentionExport(workspaceId, actorId, snapshot)
                        : null;
        int pruned = snapshot.cutoff() == null
                ? 0
                : automationWorkerRunRepository.deleteRetainedRunsFiltered(
                        workspaceId,
                        snapshot.filter().workerType(),
                        snapshot.filter().triggerType(),
                        snapshot.filter().status(),
                        snapshot.filter().startedFrom(),
                        snapshot.filter().startedTo(),
                        snapshot.cutoff()
                );
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("runsEligible", snapshot.runsEligible())
                .put("runsPruned", pruned);
        appendWorkerRunRetentionFilter(payload, snapshot.filter());
        if (actorId != null) {
            payload.put("actorUserId", actorId.toString());
        }
        if (snapshot.cutoff() != null) {
            payload.put("cutoff", snapshot.cutoff().toString());
        }
        if (export != null) {
            payload.put("exportJobId", export.exportJobId().toString());
            payload.put("fileAttachmentId", export.fileAttachmentId().toString());
        }
        domainEventService.record(workspaceId, "automation_worker_settings", workspaceId, "automation_worker_runs.pruned", payload);
        return workerRunRetentionResponse(workspaceId, snapshot, export, pruned);
    }

    @Transactional
    public AutomationWorkerRunResponse runQueuedJobsInternal(UUID workspaceId, Integer limit, UUID actorId) {
        AutomationWorkerRun run = startWorkerRun(workspaceId, "automation", actorId, null, normalizeWorkerLimit(limit), null);
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
        finishWorkerRun(run, responses.size(), succeeded, failed, 0, null);
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
        return processWebhookDeliveriesInternal(workspaceId, request, actorId);
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryResponse getWebhookDelivery(UUID deliveryId) {
        UUID actorId = currentUserService.requireUserId();
        WebhookDelivery delivery = webhookDelivery(deliveryId);
        Webhook webhook = webhook(delivery.getWebhookId());
        permissionService.requireWorkspacePermission(actorId, webhook.getWorkspaceId(), "automation.admin");
        return WebhookDeliveryResponse.from(delivery);
    }

    @Transactional
    public WebhookDeliveryResponse retryWebhookDelivery(UUID deliveryId) {
        UUID actorId = currentUserService.requireUserId();
        WebhookDelivery delivery = webhookDelivery(deliveryId);
        Webhook webhook = webhook(delivery.getWebhookId());
        permissionService.requireWorkspacePermission(actorId, webhook.getWorkspaceId(), "automation.admin");
        if ("delivered".equals(delivery.getStatus())) {
            throw badRequest("Delivered webhook deliveries cannot be retried");
        }
        delivery.setStatus("queued");
        delivery.setNextRetryAt(null);
        delivery.setResponseBody(null);
        delivery.setResponseCode(null);
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);
        recordWebhookDeliveryEvent(saved, webhook, "webhook_delivery.retry_queued", actorId);
        return WebhookDeliveryResponse.from(saved);
    }

    @Transactional
    public WebhookDeliveryResponse cancelWebhookDelivery(UUID deliveryId) {
        UUID actorId = currentUserService.requireUserId();
        WebhookDelivery delivery = webhookDelivery(deliveryId);
        Webhook webhook = webhook(delivery.getWebhookId());
        permissionService.requireWorkspacePermission(actorId, webhook.getWorkspaceId(), "automation.admin");
        if ("delivered".equals(delivery.getStatus())) {
            throw badRequest("Delivered webhook deliveries cannot be cancelled");
        }
        delivery.setStatus("cancelled");
        delivery.setNextRetryAt(null);
        WebhookDelivery saved = webhookDeliveryRepository.save(delivery);
        recordWebhookDeliveryEvent(saved, webhook, "webhook_delivery.cancelled", actorId);
        return WebhookDeliveryResponse.from(saved);
    }

    @Transactional
    public WebhookDeliveryWorkerResponse processWebhookDeliveriesInternal(UUID workspaceId, WebhookDeliveryWorkerRequest request, UUID actorId) {
        int limit = normalizeWorkerLimit(request == null ? null : request.limit());
        int maxAttempts = normalizeMaxAttempts(request == null ? null : request.maxAttempts());
        boolean dryRun = request == null || !Boolean.FALSE.equals(request.dryRun());
        AutomationWorkerRun run = startWorkerRun(workspaceId, "webhook", actorId, dryRun, limit, maxAttempts);
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
        finishWorkerRun(run, responses.size(), delivered, failed, deadLettered, null);
        return new WebhookDeliveryWorkerResponse(workspaceId, responses.size(), delivered, failed, deadLettered, responses);
    }

    @Transactional(readOnly = true)
    public List<EmailDeliveryResponse> listEmailDeliveries(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return emailDeliveryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(EmailDeliveryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailDeliveryResponse getEmailDelivery(UUID deliveryId) {
        UUID actorId = currentUserService.requireUserId();
        EmailDelivery delivery = emailDelivery(deliveryId);
        permissionService.requireWorkspacePermission(actorId, delivery.getWorkspaceId(), "automation.admin");
        return EmailDeliveryResponse.from(delivery);
    }

    @Transactional
    public EmailDeliveryResponse retryEmailDelivery(UUID deliveryId) {
        UUID actorId = currentUserService.requireUserId();
        EmailDelivery delivery = emailDelivery(deliveryId);
        permissionService.requireWorkspacePermission(actorId, delivery.getWorkspaceId(), "automation.admin");
        if ("sent".equals(delivery.getStatus())) {
            throw badRequest("Sent email deliveries cannot be retried");
        }
        delivery.setStatus("queued");
        delivery.setNextRetryAt(null);
        delivery.setResponseBody(null);
        EmailDelivery saved = emailDeliveryRepository.save(delivery);
        recordEmailDeliveryEvent(saved, "email_delivery.retry_queued", actorId);
        return EmailDeliveryResponse.from(saved);
    }

    @Transactional
    public EmailDeliveryResponse cancelEmailDelivery(UUID deliveryId) {
        UUID actorId = currentUserService.requireUserId();
        EmailDelivery delivery = emailDelivery(deliveryId);
        permissionService.requireWorkspacePermission(actorId, delivery.getWorkspaceId(), "automation.admin");
        if ("sent".equals(delivery.getStatus())) {
            throw badRequest("Sent email deliveries cannot be cancelled");
        }
        delivery.setStatus("cancelled");
        delivery.setNextRetryAt(null);
        EmailDelivery saved = emailDeliveryRepository.save(delivery);
        recordEmailDeliveryEvent(saved, "email_delivery.cancelled", actorId);
        return EmailDeliveryResponse.from(saved);
    }

    @Transactional
    public EmailDeliveryWorkerResponse processEmailDeliveries(UUID workspaceId, EmailDeliveryWorkerRequest request) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "automation.admin");
        return processEmailDeliveriesInternal(workspaceId, request, actorId);
    }

    @Transactional
    public EmailDeliveryWorkerResponse processEmailDeliveriesInternal(UUID workspaceId, EmailDeliveryWorkerRequest request, UUID actorId) {
        int limit = normalizeWorkerLimit(request == null ? null : request.limit());
        int maxAttempts = normalizeMaxAttempts(request == null ? null : request.maxAttempts());
        boolean dryRun = request == null || !Boolean.FALSE.equals(request.dryRun());
        AutomationWorkerRun run = startWorkerRun(workspaceId, "email", actorId, dryRun, limit, maxAttempts);
        List<EmailDelivery> deliveries = emailDeliveryRepository.findProcessableDeliveries(workspaceId, OffsetDateTime.now(), limit);
        List<EmailDeliveryResponse> responses = new ArrayList<>();
        int sent = 0;
        int failed = 0;
        int deadLettered = 0;
        for (EmailDelivery delivery : deliveries) {
            processEmailDelivery(delivery, maxAttempts, dryRun);
            if ("sent".equals(delivery.getStatus())) {
                sent++;
            } else if ("dead_letter".equals(delivery.getStatus())) {
                deadLettered++;
            } else {
                failed++;
            }
            if (actorId != null) {
                recordEmailDeliveryEvent(delivery, "email_delivery.processed", actorId);
            }
            responses.add(EmailDeliveryResponse.from(delivery));
        }
        finishWorkerRun(run, responses.size(), sent, failed, deadLettered, null);
        return new EmailDeliveryWorkerResponse(workspaceId, responses.size(), sent, failed, deadLettered, responses);
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
            case "email" -> runEmailAction(rule, job, action, actorId);
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
            URI webhookUri = URI.create(webhook.getUrl());
            outboundUrlPolicy.validateResolvedHttpUri(webhookUri, "webhook.url");
            String body = delivery.getPayload() == null ? "{}" : delivery.getPayload().toString();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(webhookUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Trasck-Event-Type", delivery.getEventType())
                    .header("X-Trasck-Webhook-Id", webhook.getId().toString())
                    .header("X-Trasck-Webhook-Delivery-Id", delivery.getId().toString());
            addWebhookSignatureHeaders(requestBuilder, webhook, body);
            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(body))
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

    private void processEmailDelivery(EmailDelivery delivery, int maxAttempts, boolean dryRun) {
        int attempt = delivery.getAttemptCount() == null ? 1 : delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attempt);
        if (dryRun) {
            delivery.setStatus("sent");
            delivery.setSentAt(OffsetDateTime.now());
            delivery.setResponseBody("Dry-run email accepted by Trasck " + delivery.getProvider() + " provider");
            delivery.setNextRetryAt(null);
            emailDeliveryRepository.save(delivery);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(firstText(delivery.getFromEmail(), defaultFromEmail));
            message.setTo(delivery.getRecipientEmail());
            message.setSubject(delivery.getSubject());
            message.setText(delivery.getBody());
            mailSender(delivery).send(message);
            delivery.setStatus("sent");
            delivery.setSentAt(OffsetDateTime.now());
            delivery.setResponseBody("Email accepted by " + delivery.getProvider());
            delivery.setNextRetryAt(null);
        } catch (Exception ex) {
            markEmailDeliveryFailed(delivery, attempt, maxAttempts, ex.getMessage());
        }
        emailDeliveryRepository.save(delivery);
    }

    private void markWebhookDeliveryFailed(WebhookDelivery delivery, int attempt, int maxAttempts, String message) {
        delivery.setStatus(attempt >= maxAttempts ? "dead_letter" : "failed");
        delivery.setResponseBody(truncate(message, 4000));
        delivery.setNextRetryAt(attempt >= maxAttempts ? null : OffsetDateTime.now().plusMinutes(Math.min(60, attempt * 5L)));
    }

    private void markEmailDeliveryFailed(EmailDelivery delivery, int attempt, int maxAttempts, String message) {
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

    private void runEmailAction(AutomationRule rule, AutomationExecutionJob job, AutomationAction action, UUID actorId) {
        JsonNode config = jsonObject(action.getConfig());
        EmailProviderSettings providerSettings = activeEmailProviderSettings(rule.getWorkspaceId());
        String recipient = firstText(
                text(config, "toEmail", null),
                text(config, "recipientEmail", null),
                text(config, "to", null),
                userEmail(config, "userId"),
                actorId == null ? null : userEmail(actorId)
        );
        if (!hasText(recipient)) {
            throw badRequest("Email action config must include to, toEmail, recipientEmail, userId, or run as a known actor");
        }
        String provider = normalizeEmailProvider(text(config, "provider", providerSettings.getProvider()));
        if ("maildev".equals(provider) && !maildevSelectable()) {
            throw badRequest("Maildev email provider is only selectable outside production profiles");
        }
        EmailDelivery delivery = new EmailDelivery();
        delivery.setWorkspaceId(rule.getWorkspaceId());
        delivery.setAutomationJobId(job.getId());
        delivery.setActionId(action.getId());
        delivery.setProvider(provider);
        delivery.setFromEmail(text(config, "fromEmail", providerSettings.getFromEmail()));
        delivery.setRecipientEmail(recipient);
        delivery.setSubject(requiredText(text(config, "subject", null), "config.subject"));
        delivery.setBody(text(config, "body", ""));
        delivery.setStatus("queued");
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(OffsetDateTime.now());
        EmailDelivery saved = emailDeliveryRepository.save(delivery);
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("emailDeliveryId", saved.getId().toString())
                .put("provider", saved.getProvider())
                .put("recipientEmail", saved.getRecipientEmail());
        log(job, action, "queued", "Email delivery queued for " + saved.getProvider(), metadata);
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
            outboundUrlPolicy.validateHttpUrl(url, "url");
            webhook.setUrl(url);
        }
        if (hasText(request.secret())) {
            webhook.setSecretHash(sha256(request.secret()));
            webhook.setSecretEncrypted(secretCipherService.encrypt(request.secret()));
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

    private WebhookDelivery webhookDelivery(UUID deliveryId) {
        return webhookDeliveryRepository.findById(deliveryId).orElseThrow(() -> notFound("Webhook delivery not found"));
    }

    private EmailDelivery emailDelivery(UUID deliveryId) {
        return emailDeliveryRepository.findById(deliveryId).orElseThrow(() -> notFound("Email delivery not found"));
    }

    private EmailProviderSettings activeEmailProviderSettings(UUID workspaceId) {
        return emailProviderSettingsRepository.findByWorkspaceId(workspaceId)
                .filter(settings -> Boolean.TRUE.equals(settings.getActive()))
                .orElseGet(() -> defaultEmailProviderSettings(workspaceId));
    }

    private EmailProviderSettings defaultEmailProviderSettings(UUID workspaceId) {
        EmailProviderSettings settings = new EmailProviderSettings();
        settings.setWorkspaceId(workspaceId);
        settings.setProvider(normalizeEmailProvider(emailProvider));
        settings.setFromEmail(firstText(defaultFromEmail, "no-reply@trasck.local"));
        settings.setSmtpStartTlsEnabled(true);
        settings.setSmtpAuthEnabled(true);
        settings.setActive(true);
        return settings;
    }

    private void applyEmailProviderSettings(EmailProviderSettings settings, EmailProviderSettingsRequest request) {
        String provider = request.provider() == null ? settings.getProvider() : normalizeEmailProvider(request.provider());
        if ("maildev".equals(provider) && !maildevSelectable()) {
            throw badRequest("Maildev email provider is only selectable outside production profiles");
        }
        settings.setProvider(provider);
        if (request.fromEmail() != null) {
            settings.setFromEmail(requiredText(request.fromEmail(), "fromEmail").toLowerCase());
        } else if (!hasText(settings.getFromEmail())) {
            settings.setFromEmail(firstText(defaultFromEmail, "no-reply@trasck.local"));
        }
        if (request.smtpHost() != null) {
            settings.setSmtpHost(hasText(request.smtpHost()) ? request.smtpHost().trim() : null);
        }
        if (request.smtpPort() != null) {
            if (request.smtpPort() < 1 || request.smtpPort() > 65_535) {
                throw badRequest("smtpPort must be between 1 and 65535");
            }
            settings.setSmtpPort(request.smtpPort());
        }
        if (request.smtpUsername() != null) {
            settings.setSmtpUsername(hasText(request.smtpUsername()) ? request.smtpUsername().trim() : null);
        }
        if (Boolean.TRUE.equals(request.clearSmtpPassword())) {
            settings.setSmtpPasswordEncrypted(null);
        } else if (hasText(request.smtpPassword())) {
            settings.setSmtpPasswordEncrypted(secretCipherService.encrypt(request.smtpPassword()));
        }
        if (request.smtpStartTlsEnabled() != null) {
            settings.setSmtpStartTlsEnabled(request.smtpStartTlsEnabled());
        } else if (settings.getSmtpStartTlsEnabled() == null) {
            settings.setSmtpStartTlsEnabled(true);
        }
        if (request.smtpAuthEnabled() != null) {
            settings.setSmtpAuthEnabled(request.smtpAuthEnabled());
        } else if (settings.getSmtpAuthEnabled() == null) {
            settings.setSmtpAuthEnabled(true);
        }
        if (request.active() != null) {
            settings.setActive(request.active());
        } else if (settings.getActive() == null) {
            settings.setActive(true);
        }
        if ("smtp".equals(settings.getProvider())) {
            if (!hasText(settings.getSmtpHost())) {
                throw badRequest("smtpHost is required for the smtp provider");
            }
            if (settings.getSmtpPort() == null) {
                throw badRequest("smtpPort is required for the smtp provider");
            }
        }
    }

    private JavaMailSender mailSender(EmailDelivery delivery) {
        if (!"smtp".equals(normalizeEmailProvider(delivery.getProvider()))) {
            return mailSenderProvider.getObject();
        }
        EmailProviderSettings settings = emailProviderSettingsRepository.findByWorkspaceId(delivery.getWorkspaceId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .filter(candidate -> "smtp".equals(candidate.getProvider()))
                .orElseThrow(() -> badRequest("Active SMTP settings are required to send this email delivery"));
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(requiredText(settings.getSmtpHost(), "smtpHost"));
        sender.setPort(required(settings.getSmtpPort(), "smtpPort"));
        if (hasText(settings.getSmtpUsername())) {
            sender.setUsername(settings.getSmtpUsername());
        }
        if (hasText(settings.getSmtpPasswordEncrypted())) {
            sender.setPassword(secretCipherService.decrypt(settings.getSmtpPasswordEncrypted()));
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(Boolean.TRUE.equals(settings.getSmtpAuthEnabled())));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(Boolean.TRUE.equals(settings.getSmtpStartTlsEnabled())));
        return sender;
    }

    private boolean maildevSelectable() {
        return Arrays.stream(environment.getActiveProfiles())
                .noneMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    private String normalizeEmailProvider(String provider) {
        String normalized = hasText(provider) ? provider.trim().toLowerCase() : "maildev";
        if (!List.of("maildev", "smtp").contains(normalized)) {
            throw badRequest("email provider must be maildev or smtp");
        }
        return normalized;
    }

    private AutomationWorkerSettings workerSettings(UUID workspaceId) {
        return automationWorkerSettingsRepository.findById(workspaceId).orElseGet(() -> {
            AutomationWorkerSettings settings = new AutomationWorkerSettings();
            settings.setWorkspaceId(workspaceId);
            settings.setAutomationJobsEnabled(false);
            settings.setWebhookDeliveriesEnabled(false);
            settings.setEmailDeliveriesEnabled(false);
            settings.setImportConflictResolutionEnabled(false);
            settings.setImportReviewExportsEnabled(false);
            settings.setAutomationLimit(25);
            settings.setWebhookLimit(25);
            settings.setEmailLimit(25);
            settings.setImportConflictResolutionLimit(10);
            settings.setImportReviewExportLimit(10);
            settings.setWebhookMaxAttempts(3);
            settings.setEmailMaxAttempts(3);
            settings.setWebhookDryRun(true);
            settings.setEmailDryRun(true);
            settings.setWorkerRunRetentionEnabled(false);
            settings.setWorkerRunRetentionDays(null);
            settings.setWorkerRunExportBeforePrune(true);
            settings.setWorkerRunPruningAutomaticEnabled(false);
            settings.setWorkerRunPruningIntervalMinutes(1440);
            settings.setAgentDispatchAttemptRetentionEnabled(false);
            settings.setAgentDispatchAttemptRetentionDays(30);
            settings.setAgentDispatchAttemptExportBeforePrune(true);
            settings.setAgentDispatchAttemptPruningAutomaticEnabled(false);
            settings.setAgentDispatchAttemptPruningIntervalMinutes(1440);
            return settings;
        });
    }

    private boolean shouldRunAutomaticWorkerRunPruning(
            Workspace workspace,
            AutomationWorkerSettings settings,
            OffsetDateTime now
    ) {
        if (!Boolean.TRUE.equals(settings.getWorkerRunPruningAutomaticEnabled())
                || !Boolean.TRUE.equals(settings.getWorkerRunRetentionEnabled())) {
            return false;
        }
        int intervalMinutes = normalizePruningIntervalMinutes(settings.getWorkerRunPruningIntervalMinutes());
        OffsetDateTime lastStartedAt = settings.getWorkerRunPruningLastStartedAt();
        if (lastStartedAt != null && lastStartedAt.plusMinutes(intervalMinutes).isAfter(now)) {
            return false;
        }
        return withinPruningWindow(workspace, settings, now);
    }

    private boolean withinPruningWindow(Workspace workspace, AutomationWorkerSettings settings, OffsetDateTime now) {
        LocalTime start = settings.getWorkerRunPruningWindowStart();
        LocalTime end = settings.getWorkerRunPruningWindowEnd();
        if (start == null && end == null) {
            return true;
        }
        LocalTime localNow = now.atZoneSameInstant(workspaceZone(workspace)).toLocalTime();
        if (start != null && end != null) {
            if (start.equals(end)) {
                return true;
            }
            if (start.isBefore(end)) {
                return !localNow.isBefore(start) && !localNow.isAfter(end);
            }
            return !localNow.isBefore(start) || !localNow.isAfter(end);
        }
        if (start != null) {
            return !localNow.isBefore(start);
        }
        return !localNow.isAfter(end);
    }

    private ZoneId workspaceZone(Workspace workspace) {
        if (workspace != null && hasText(workspace.getTimezone())) {
            try {
                return ZoneId.of(workspace.getTimezone());
            } catch (DateTimeException ignored) {
                return ZoneOffset.UTC;
            }
        }
        return ZoneOffset.UTC;
    }

    private WorkerRunRetentionSnapshot workerRunRetentionSnapshot(UUID workspaceId, int limit, WorkerRunRetentionFilter filter) {
        AutomationWorkerSettings settings = workerSettings(workspaceId);
        OffsetDateTime cutoff = workerRunRetentionCutoff(settings);
        long eligible = cutoff == null
                ? 0
                : automationWorkerRunRepository.countRetainedRunsFiltered(
                        workspaceId,
                        filter.workerType(),
                        filter.triggerType(),
                        filter.status(),
                        filter.startedFrom(),
                        filter.startedTo(),
                        cutoff
                );
        List<AutomationWorkerRunHistoryResponse> runs = cutoff == null
                ? List.of()
                : retainedWorkerRuns(workspaceId, filter, cutoff, limit).stream()
                        .map(AutomationWorkerRunHistoryResponse::from)
                        .toList();
        return new WorkerRunRetentionSnapshot(settings, filter, cutoff, eligible, runs);
    }

    private List<AutomationWorkerRun> retainedWorkerRuns(UUID workspaceId, WorkerRunRetentionFilter filter, OffsetDateTime cutoff, int limit) {
        return automationWorkerRunRepository.findRetainedRunsFiltered(
                workspaceId,
                filter.workerType(),
                filter.triggerType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                cutoff,
                limit
        );
    }

    private OffsetDateTime workerRunRetentionCutoff(AutomationWorkerSettings settings) {
        if (!Boolean.TRUE.equals(settings.getWorkerRunRetentionEnabled()) || settings.getWorkerRunRetentionDays() == null) {
            return null;
        }
        return OffsetDateTime.now().minusDays(settings.getWorkerRunRetentionDays());
    }

    private StoredWorkerRunRetentionExport storeWorkerRunRetentionExport(
            UUID workspaceId,
            UUID actorId,
            WorkerRunRetentionSnapshot snapshot
    ) {
        AttachmentStorageConfig storageConfig = attachmentStorageConfigRepository.findFirstByWorkspaceIdAndActiveTrueAndDefaultConfigTrue(workspaceId)
                .orElseThrow(() -> badRequest("Default attachment storage config not found"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String filename = "automation-worker-runs-"
                + workspaceId
                + workerRunRetentionFilenameSuffix(snapshot.filter())
                + "-"
                + now.format(EXPORT_FILENAME_TIME)
                + ".json";
        byte[] content = workerRunRetentionExportContent(workspaceId, actorId, snapshot, now);
        contentLimitPolicy.validateGeneratedExport(workspaceId, filename, "application/json", content);
        StoredAttachment stored = attachmentStorageService.store(
                storageConfig,
                new AttachmentUpload(filename, "application/json", content, null)
        );
        try {
            Attachment attachment = new Attachment();
            attachment.setWorkspaceId(workspaceId);
            attachment.setStorageConfigId(storageConfig.getId());
            attachment.setUploaderId(actorId);
            attachment.setFilename(filename);
            attachment.setContentType("application/json");
            attachment.setStorageKey(stored.storageKey());
            attachment.setSizeBytes(stored.sizeBytes());
            attachment.setChecksum(stored.checksum());
            attachment.setVisibility("restricted");
            Attachment savedAttachment = attachmentRepository.save(attachment);

            ExportJob exportJob = new ExportJob();
            exportJob.setWorkspaceId(workspaceId);
            exportJob.setRequestedById(actorId);
            exportJob.setExportType("automation_worker_runs");
            exportJob.setStatus("completed");
            exportJob.setFileAttachmentId(savedAttachment.getId());
            exportJob.setRequestPayload(workerRunRetentionFilterPayload(snapshot.filter()));
            exportJob.setCreatedAt(now);
            exportJob.setStartedAt(now);
            exportJob.setFinishedAt(now);
            ExportJob savedExportJob = exportJobRepository.save(exportJob);
            return new StoredWorkerRunRetentionExport(savedExportJob.getId(), savedAttachment.getId());
        } catch (RuntimeException ex) {
            attachmentStorageService.delete(storageConfig, stored.storageKey());
            throw ex;
        }
    }

    private byte[] workerRunRetentionExportContent(
            UUID workspaceId,
            UUID actorId,
            WorkerRunRetentionSnapshot snapshot,
            OffsetDateTime exportedAt
    ) {
        ObjectNode document = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("exportedAt", exportedAt.toString())
                .put("retentionEnabled", Boolean.TRUE.equals(snapshot.settings().getWorkerRunRetentionEnabled()))
                .put("exportBeforePrune", Boolean.TRUE.equals(snapshot.settings().getWorkerRunExportBeforePrune()))
                .put("runsEligible", snapshot.runsEligible())
                .put("runsIncluded", snapshot.runs().size());
        appendWorkerRunRetentionFilter(document, snapshot.filter());
        if (actorId != null) {
            document.put("actorUserId", actorId.toString());
        }
        if (snapshot.settings().getWorkerRunRetentionDays() != null) {
            document.put("retentionDays", snapshot.settings().getWorkerRunRetentionDays());
        }
        if (snapshot.cutoff() != null) {
            document.put("cutoff", snapshot.cutoff().toString());
        }
        document.set("runs", objectMapper.valueToTree(snapshot.runs()));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize automation worker run retention export", ex);
        }
    }

    private AutomationWorkerRunRetentionResponse workerRunRetentionResponse(
            UUID workspaceId,
            WorkerRunRetentionSnapshot snapshot,
            StoredWorkerRunRetentionExport export,
            int pruned
    ) {
        return new AutomationWorkerRunRetentionResponse(
                workspaceId,
                snapshot.filter().workerType(),
                snapshot.filter().triggerType(),
                snapshot.filter().status(),
                snapshot.filter().startedFrom(),
                snapshot.filter().startedTo(),
                Boolean.TRUE.equals(snapshot.settings().getWorkerRunRetentionEnabled()),
                snapshot.settings().getWorkerRunRetentionDays(),
                Boolean.TRUE.equals(snapshot.settings().getWorkerRunExportBeforePrune()),
                snapshot.cutoff(),
                snapshot.runsEligible(),
                snapshot.runs().size(),
                pruned,
                export == null ? null : export.exportJobId(),
                export == null ? null : export.fileAttachmentId(),
                snapshot.runs()
        );
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

    private void recordWebhookDeliveryEvent(WebhookDelivery delivery, Webhook webhook, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("webhookDeliveryId", delivery.getId().toString())
                .put("webhookId", webhook.getId().toString())
                .put("status", delivery.getStatus())
                .put("actorUserId", actorId.toString());
        domainEventService.record(webhook.getWorkspaceId(), "webhook_delivery", delivery.getId(), eventType, payload);
    }

    private void recordEmailDeliveryEvent(EmailDelivery delivery, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("emailDeliveryId", delivery.getId().toString())
                .put("status", delivery.getStatus())
                .put("actorUserId", actorId.toString());
        domainEventService.record(delivery.getWorkspaceId(), "email_delivery", delivery.getId(), eventType, payload);
    }

    private void applyWorkerSettings(AutomationWorkerSettings settings, AutomationWorkerSettingsRequest request) {
        if (request.automationJobsEnabled() != null) {
            settings.setAutomationJobsEnabled(request.automationJobsEnabled());
        }
        if (request.webhookDeliveriesEnabled() != null) {
            settings.setWebhookDeliveriesEnabled(request.webhookDeliveriesEnabled());
        }
        if (request.emailDeliveriesEnabled() != null) {
            settings.setEmailDeliveriesEnabled(request.emailDeliveriesEnabled());
        }
        if (request.importConflictResolutionEnabled() != null) {
            settings.setImportConflictResolutionEnabled(request.importConflictResolutionEnabled());
        }
        if (request.importReviewExportsEnabled() != null) {
            settings.setImportReviewExportsEnabled(request.importReviewExportsEnabled());
        }
        if (request.automationLimit() != null) {
            settings.setAutomationLimit(normalizeWorkerLimit(request.automationLimit()));
        }
        if (request.webhookLimit() != null) {
            settings.setWebhookLimit(normalizeWorkerLimit(request.webhookLimit()));
        }
        if (request.emailLimit() != null) {
            settings.setEmailLimit(normalizeWorkerLimit(request.emailLimit()));
        }
        if (request.importConflictResolutionLimit() != null) {
            settings.setImportConflictResolutionLimit(normalizeWorkerLimit(request.importConflictResolutionLimit()));
        }
        if (request.importReviewExportLimit() != null) {
            settings.setImportReviewExportLimit(normalizeImportReviewExportLimit(request.importReviewExportLimit()));
        }
        if (request.webhookMaxAttempts() != null) {
            settings.setWebhookMaxAttempts(normalizeMaxAttempts(request.webhookMaxAttempts()));
        }
        if (request.emailMaxAttempts() != null) {
            settings.setEmailMaxAttempts(normalizeMaxAttempts(request.emailMaxAttempts()));
        }
        if (request.webhookDryRun() != null) {
            settings.setWebhookDryRun(request.webhookDryRun());
        }
        if (request.emailDryRun() != null) {
            settings.setEmailDryRun(request.emailDryRun());
        }
        if (request.workerRunRetentionEnabled() != null) {
            settings.setWorkerRunRetentionEnabled(request.workerRunRetentionEnabled());
        }
        if (request.workerRunRetentionDays() != null) {
            if (request.workerRunRetentionDays() < 1) {
                throw badRequest("workerRunRetentionDays must be greater than 0");
            }
            settings.setWorkerRunRetentionDays(request.workerRunRetentionDays());
        }
        if (request.workerRunExportBeforePrune() != null) {
            settings.setWorkerRunExportBeforePrune(request.workerRunExportBeforePrune());
        }
        if (request.workerRunPruningAutomaticEnabled() != null) {
            settings.setWorkerRunPruningAutomaticEnabled(request.workerRunPruningAutomaticEnabled());
        }
        if (request.workerRunPruningIntervalMinutes() != null) {
            settings.setWorkerRunPruningIntervalMinutes(normalizePruningIntervalMinutes(request.workerRunPruningIntervalMinutes()));
        }
        if (request.workerRunPruningWindowStart() != null) {
            settings.setWorkerRunPruningWindowStart(request.workerRunPruningWindowStart());
        }
        if (request.workerRunPruningWindowEnd() != null) {
            settings.setWorkerRunPruningWindowEnd(request.workerRunPruningWindowEnd());
        }
        if (request.agentDispatchAttemptRetentionEnabled() != null) {
            settings.setAgentDispatchAttemptRetentionEnabled(request.agentDispatchAttemptRetentionEnabled());
        }
        if (request.agentDispatchAttemptRetentionDays() != null) {
            settings.setAgentDispatchAttemptRetentionDays(normalizeAgentDispatchAttemptRetentionDays(request.agentDispatchAttemptRetentionDays()));
        }
        if (request.agentDispatchAttemptExportBeforePrune() != null) {
            settings.setAgentDispatchAttemptExportBeforePrune(request.agentDispatchAttemptExportBeforePrune());
        }
        if (request.agentDispatchAttemptPruningAutomaticEnabled() != null) {
            settings.setAgentDispatchAttemptPruningAutomaticEnabled(request.agentDispatchAttemptPruningAutomaticEnabled());
        }
        if (request.agentDispatchAttemptPruningIntervalMinutes() != null) {
            settings.setAgentDispatchAttemptPruningIntervalMinutes(normalizeAgentDispatchAttemptPruningIntervalMinutes(request.agentDispatchAttemptPruningIntervalMinutes()));
        }
        if (request.agentDispatchAttemptPruningWindowStart() != null) {
            settings.setAgentDispatchAttemptPruningWindowStart(request.agentDispatchAttemptPruningWindowStart());
        }
        if (request.agentDispatchAttemptPruningWindowEnd() != null) {
            settings.setAgentDispatchAttemptPruningWindowEnd(request.agentDispatchAttemptPruningWindowEnd());
        }
        if (Boolean.TRUE.equals(settings.getWorkerRunRetentionEnabled()) && settings.getWorkerRunRetentionDays() == null) {
            throw badRequest("workerRunRetentionDays is required when worker run retention is enabled");
        }
        if (Boolean.TRUE.equals(settings.getWorkerRunPruningAutomaticEnabled()) && !Boolean.TRUE.equals(settings.getWorkerRunRetentionEnabled())) {
            throw badRequest("workerRunRetentionEnabled is required when automatic worker run pruning is enabled");
        }
        if (Boolean.TRUE.equals(settings.getAgentDispatchAttemptPruningAutomaticEnabled()) && !Boolean.TRUE.equals(settings.getAgentDispatchAttemptRetentionEnabled())) {
            throw badRequest("agentDispatchAttemptRetentionEnabled is required when automatic agent dispatch attempt pruning is enabled");
        }
        if (settings.getWorkerRunExportBeforePrune() == null) {
            settings.setWorkerRunExportBeforePrune(true);
        }
        if (settings.getWorkerRunPruningAutomaticEnabled() == null) {
            settings.setWorkerRunPruningAutomaticEnabled(false);
        }
        if (settings.getWorkerRunPruningIntervalMinutes() == null) {
            settings.setWorkerRunPruningIntervalMinutes(1440);
        }
        if (settings.getImportConflictResolutionEnabled() == null) {
            settings.setImportConflictResolutionEnabled(false);
        }
        if (settings.getImportConflictResolutionLimit() == null) {
            settings.setImportConflictResolutionLimit(10);
        }
        if (settings.getImportReviewExportsEnabled() == null) {
            settings.setImportReviewExportsEnabled(false);
        }
        if (settings.getImportReviewExportLimit() == null) {
            settings.setImportReviewExportLimit(10);
        }
        if (settings.getAgentDispatchAttemptRetentionEnabled() == null) {
            settings.setAgentDispatchAttemptRetentionEnabled(false);
        }
        if (settings.getAgentDispatchAttemptRetentionDays() == null) {
            settings.setAgentDispatchAttemptRetentionDays(30);
        }
        if (settings.getAgentDispatchAttemptExportBeforePrune() == null) {
            settings.setAgentDispatchAttemptExportBeforePrune(true);
        }
        if (settings.getAgentDispatchAttemptPruningAutomaticEnabled() == null) {
            settings.setAgentDispatchAttemptPruningAutomaticEnabled(false);
        }
        if (settings.getAgentDispatchAttemptPruningIntervalMinutes() == null) {
            settings.setAgentDispatchAttemptPruningIntervalMinutes(1440);
        }
    }

    private int normalizeImportReviewExportLimit(Integer limit) {
        int value = limit == null ? 10 : limit;
        if (value < 1 || value > 50) {
            throw badRequest("importReviewExportLimit must be between 1 and 50");
        }
        return value;
    }

    private int normalizeAgentDispatchAttemptRetentionDays(Integer days) {
        int value = days == null ? 30 : days;
        if (value < 1 || value > 3650) {
            throw badRequest("agentDispatchAttemptRetentionDays must be between 1 and 3650");
        }
        return value;
    }

    private int normalizePruningIntervalMinutes(Integer minutes) {
        int value = minutes == null ? 1440 : minutes;
        if (value < 5 || value > 10080) {
            throw badRequest("workerRunPruningIntervalMinutes must be between 5 and 10080");
        }
        return value;
    }

    private int normalizeAgentDispatchAttemptPruningIntervalMinutes(Integer minutes) {
        int value = minutes == null ? 1440 : minutes;
        if (value < 5 || value > 10080) {
            throw badRequest("agentDispatchAttemptPruningIntervalMinutes must be between 5 and 10080");
        }
        return value;
    }

    private AutomationWorkerRun startWorkerRun(
            UUID workspaceId,
            String workerType,
            UUID actorId,
            Boolean dryRun,
            Integer limit,
            Integer maxAttempts
    ) {
        AutomationWorkerRun run = new AutomationWorkerRun();
        run.setWorkspaceId(workspaceId);
        run.setWorkerType(workerType);
        run.setTriggerType(actorId == null ? "scheduled" : "manual");
        run.setStatus("running");
        run.setDryRun(dryRun);
        run.setRequestedLimit(limit);
        run.setMaxAttempts(maxAttempts);
        run.setProcessedCount(0);
        run.setSuccessCount(0);
        run.setFailureCount(0);
        run.setDeadLetterCount(0);
        ObjectNode metadata = objectMapper.createObjectNode();
        if (actorId != null) {
            metadata.put("actorUserId", actorId.toString());
        }
        run.setMetadata(metadata);
        run.setStartedAt(OffsetDateTime.now());
        AutomationWorkerRun saved = automationWorkerRunRepository.save(run);
        updateWorkerHealth(saved);
        return saved;
    }

    private void finishWorkerRun(
            AutomationWorkerRun run,
            int processed,
            int succeeded,
            int failed,
            int deadLettered,
            String errorMessage
    ) {
        run.setProcessedCount(processed);
        run.setSuccessCount(succeeded);
        run.setFailureCount(failed);
        run.setDeadLetterCount(deadLettered);
        run.setErrorMessage(truncate(errorMessage, 4000));
        run.setStatus(hasText(errorMessage) || failed > 0 || deadLettered > 0 ? "failed" : "succeeded");
        run.setFinishedAt(OffsetDateTime.now());
        AutomationWorkerRun saved = automationWorkerRunRepository.save(run);
        updateWorkerHealth(saved);
    }

    private void updateWorkerHealth(AutomationWorkerRun run) {
        AutomationWorkerHealth health = automationWorkerHealthRepository
                .findById(new AutomationWorkerHealthId(run.getWorkspaceId(), run.getWorkerType()))
                .orElseGet(() -> {
                    AutomationWorkerHealth created = new AutomationWorkerHealth();
                    created.setWorkspaceId(run.getWorkspaceId());
                    created.setWorkerType(run.getWorkerType());
                    created.setConsecutiveFailures(0);
                    return created;
                });
        health.setLastRunId(run.getId());
        health.setLastStatus(run.getStatus());
        health.setLastStartedAt(run.getStartedAt());
        health.setLastFinishedAt(run.getFinishedAt());
        health.setLastError(run.getErrorMessage());
        if ("failed".equals(run.getStatus())) {
            health.setConsecutiveFailures((health.getConsecutiveFailures() == null ? 0 : health.getConsecutiveFailures()) + 1);
        } else if ("succeeded".equals(run.getStatus())) {
            health.setConsecutiveFailures(0);
        }
        automationWorkerHealthRepository.save(health);
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

    private String userEmail(JsonNode config, String fieldName) {
        if (config == null || !config.hasNonNull(fieldName)) {
            return null;
        }
        return userEmail(uuidFromConfig(config, fieldName));
    }

    private String userEmail(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .map(User::getEmail)
                .orElse(null);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash webhook secret", ex);
        }
    }

    private void addWebhookSignatureHeaders(HttpRequest.Builder requestBuilder, Webhook webhook, String body) {
        if (!hasText(webhook.getSecretEncrypted())) {
            return;
        }
        String timestamp = String.valueOf(OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        String signature = hmacSha256(secretCipherService.decrypt(webhook.getSecretEncrypted()), timestamp + "." + body);
        requestBuilder
                .header("X-Trasck-Webhook-Timestamp", timestamp)
                .header("X-Trasck-Webhook-Signature", "sha256=" + signature);
    }

    private String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(WEBHOOK_SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), WEBHOOK_SIGNATURE_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign webhook delivery", ex);
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

    private String normalizeWorkerTypeFilter(String workerType) {
        if (!hasText(workerType) || "all".equalsIgnoreCase(workerType.trim())) {
            return null;
        }
        String normalized = workerType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!List.of("automation", "webhook", "email", "import_conflict_resolution", "import_review_export").contains(normalized)) {
            throw badRequest("workerType must be automation, webhook, email, import_conflict_resolution, or import_review_export");
        }
        return normalized;
    }

    private WorkerRunRetentionFilter workerRunRetentionFilter(
            String workerType,
            String triggerType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo
    ) {
        if (startedFrom != null && startedTo != null && startedFrom.isAfter(startedTo)) {
            throw badRequest("startedFrom must be before or equal to startedTo");
        }
        return new WorkerRunRetentionFilter(
                normalizeWorkerTypeFilter(workerType),
                normalizeWorkerRunTriggerType(triggerType),
                normalizeWorkerRunStatus(status),
                startedFrom,
                startedTo
        );
    }

    private String normalizeWorkerRunTriggerType(String triggerType) {
        if (!hasText(triggerType) || "all".equalsIgnoreCase(triggerType.trim())) {
            return null;
        }
        String normalized = triggerType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!List.of("manual", "scheduled").contains(normalized)) {
            throw badRequest("triggerType must be manual or scheduled");
        }
        return normalized;
    }

    private String normalizeWorkerRunStatus(String status) {
        if (!hasText(status) || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!List.of("running", "succeeded", "failed").contains(normalized)) {
            throw badRequest("status must be running, succeeded, or failed");
        }
        return normalized;
    }

    private String workerRunRetentionFilenameSuffix(WorkerRunRetentionFilter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.workerType() != null) {
            parts.add(filter.workerType());
        }
        if (filter.triggerType() != null) {
            parts.add(filter.triggerType());
        }
        if (filter.status() != null) {
            parts.add(filter.status());
        }
        return parts.isEmpty() ? "" : "-" + String.join("-", parts);
    }

    private ObjectNode workerRunRetentionFilterPayload(WorkerRunRetentionFilter filter) {
        ObjectNode payload = objectMapper.createObjectNode();
        appendWorkerRunRetentionFilter(payload, filter);
        return payload;
    }

    private void appendWorkerRunRetentionFilter(ObjectNode payload, WorkerRunRetentionFilter filter) {
        if (filter.workerType() != null) {
            payload.put("workerType", filter.workerType());
        }
        if (filter.triggerType() != null) {
            payload.put("triggerType", filter.triggerType());
        }
        if (filter.status() != null) {
            payload.put("status", filter.status());
        }
        if (filter.startedFrom() != null) {
            payload.put("startedFrom", filter.startedFrom().toString());
        }
        if (filter.startedTo() != null) {
            payload.put("startedTo", filter.startedTo().toString());
        }
    }

    private int normalizeRetentionExportLimit(Integer limit) {
        if (limit == null) {
            return 500;
        }
        return Math.max(1, Math.min(limit, 1000));
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

    private record WorkerRunRetentionSnapshot(
            AutomationWorkerSettings settings,
            WorkerRunRetentionFilter filter,
            OffsetDateTime cutoff,
            long runsEligible,
            List<AutomationWorkerRunHistoryResponse> runs
    ) {
    }

    private record WorkerRunRetentionFilter(
            String workerType,
            String triggerType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo
    ) {
        static WorkerRunRetentionFilter empty() {
            return new WorkerRunRetentionFilter(null, null, null, null, null);
        }
    }

    private record StoredWorkerRunRetentionExport(
            UUID exportJobId,
            UUID fileAttachmentId
    ) {
    }
}
