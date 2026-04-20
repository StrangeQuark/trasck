package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.JsonValues;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.access.Role;
import com.strangequark.trasck.access.RoleRepository;
import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.activity.Attachment;
import com.strangequark.trasck.activity.Comment;
import com.strangequark.trasck.activity.CommentRepository;
import com.strangequark.trasck.activity.AttachmentRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.activity.storage.AttachmentStorageService;
import com.strangequark.trasck.activity.storage.AttachmentUpload;
import com.strangequark.trasck.activity.storage.StoredAttachment;
import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.api.PageCursorCodec;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.event.EventConsumerConfig;
import com.strangequark.trasck.event.EventConsumerConfigRepository;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import com.strangequark.trasck.integration.ExportJob;
import com.strangequark.trasck.integration.ExportJobRepository;
import com.strangequark.trasck.integration.ExportJobResponse;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.reporting.WorkItemAssignmentHistory;
import com.strangequark.trasck.reporting.WorkItemAssignmentHistoryRepository;
import com.strangequark.trasck.reporting.WorkItemStatusHistory;
import com.strangequark.trasck.reporting.WorkItemStatusHistoryRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import com.strangequark.trasck.workflow.WorkflowAssignment;
import com.strangequark.trasck.workflow.WorkflowAssignmentRepository;
import com.strangequark.trasck.workflow.WorkflowStatus;
import com.strangequark.trasck.workflow.WorkflowStatusRepository;
import com.strangequark.trasck.workflow.WorkflowTransition;
import com.strangequark.trasck.workflow.WorkflowTransitionAction;
import com.strangequark.trasck.workflow.WorkflowTransitionActionRepository;
import com.strangequark.trasck.workflow.WorkflowTransitionRepository;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentService {

    private static final List<String> ACTIVE_TASK_STATUSES = List.of("queued", "running", "waiting_for_input");
    private static final int DEFAULT_DISPATCH_ATTEMPT_EXPORT_LIMIT = 1_000;
    private static final int MAX_DISPATCH_ATTEMPT_EXPORT_LIMIT = 10_000;
    private static final DateTimeFormatter EXPORT_FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final ObjectMapper objectMapper;
    private final AgentProviderRepository agentProviderRepository;
    private final AgentProviderCredentialRepository agentProviderCredentialRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final AgentProfileProjectRepository agentProfileProjectRepository;
    private final RepositoryConnectionRepository repositoryConnectionRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentTaskEventRepository agentTaskEventRepository;
    private final AgentMessageRepository agentMessageRepository;
    private final AgentArtifactRepository agentArtifactRepository;
    private final AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository;
    private final AgentDispatchAttemptRepository agentDispatchAttemptRepository;
    private final ExportJobRepository exportJobRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final EventConsumerConfigRepository eventConsumerConfigRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository;
    private final WorkItemStatusHistoryRepository workItemStatusHistoryRepository;
    private final WorkflowAssignmentRepository workflowAssignmentRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowTransitionActionRepository workflowTransitionActionRepository;
    private final CommentRepository commentRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;
    private final AgentCallbackJwtService callbackJwtService;
    private final SecretCipherService secretCipherService;
    private final List<AgentProviderAdapter> adapters;

    public AgentService(
            ObjectMapper objectMapper,
            AgentProviderRepository agentProviderRepository,
            AgentProviderCredentialRepository agentProviderCredentialRepository,
            AgentProfileRepository agentProfileRepository,
            AgentProfileProjectRepository agentProfileProjectRepository,
            RepositoryConnectionRepository repositoryConnectionRepository,
            AgentTaskRepository agentTaskRepository,
            AgentTaskEventRepository agentTaskEventRepository,
            AgentMessageRepository agentMessageRepository,
            AgentArtifactRepository agentArtifactRepository,
            AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository,
            AgentDispatchAttemptRepository agentDispatchAttemptRepository,
            ExportJobRepository exportJobRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            AttachmentRepository attachmentRepository,
            AttachmentStorageService attachmentStorageService,
            EventConsumerConfigRepository eventConsumerConfigRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkItemRepository workItemRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository,
            WorkItemStatusHistoryRepository workItemStatusHistoryRepository,
            WorkflowAssignmentRepository workflowAssignmentRepository,
            WorkflowStatusRepository workflowStatusRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            WorkflowTransitionActionRepository workflowTransitionActionRepository,
            CommentRepository commentRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService,
            AgentCallbackJwtService callbackJwtService,
            SecretCipherService secretCipherService,
            List<AgentProviderAdapter> adapters
    ) {
        this.objectMapper = objectMapper;
        this.agentProviderRepository = agentProviderRepository;
        this.agentProviderCredentialRepository = agentProviderCredentialRepository;
        this.agentProfileRepository = agentProfileRepository;
        this.agentProfileProjectRepository = agentProfileProjectRepository;
        this.repositoryConnectionRepository = repositoryConnectionRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.agentTaskEventRepository = agentTaskEventRepository;
        this.agentMessageRepository = agentMessageRepository;
        this.agentArtifactRepository = agentArtifactRepository;
        this.agentTaskRepositoryLinkRepository = agentTaskRepositoryLinkRepository;
        this.agentDispatchAttemptRepository = agentDispatchAttemptRepository;
        this.exportJobRepository = exportJobRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.eventConsumerConfigRepository = eventConsumerConfigRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workItemAssignmentHistoryRepository = workItemAssignmentHistoryRepository;
        this.workItemStatusHistoryRepository = workItemStatusHistoryRepository;
        this.workflowAssignmentRepository = workflowAssignmentRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.workflowTransitionActionRepository = workflowTransitionActionRepository;
        this.commentRepository = commentRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
        this.callbackJwtService = callbackJwtService;
        this.secretCipherService = secretCipherService;
        this.adapters = adapters;
    }

    @Transactional(readOnly = true)
    public List<AgentProviderResponse> listProviders(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.provider.manage");
        return agentProviderRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(AgentProviderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AgentDispatchAttemptResponse> listDispatchAttempts(
            UUID workspaceId,
            UUID agentTaskId,
            UUID providerId,
            UUID agentProfileId,
            UUID workItemId,
            String attemptType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo,
            Integer limit,
            String cursor
    ) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.provider.manage");
        AgentDispatchAttemptFilter filter = dispatchAttemptFilter(agentTaskId, providerId, agentProfileId, workItemId, attemptType, status, startedFrom, startedTo);
        int pageLimit = normalizeListLimit(limit, 50, 200);
        PageCursorCodec.TimestampCursor decoded = cursor == null || cursor.isBlank() ? null : PageCursorCodec.decodeTimestamp(cursor);
        List<AgentDispatchAttempt> page = agentDispatchAttemptRepository.findFilteredPage(
                workspaceId,
                filter.agentTaskId(),
                filter.providerId(),
                filter.agentProfileId(),
                filter.workItemId(),
                filter.attemptType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                null,
                decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.id(),
                pageLimit + 1
        );
        boolean hasMore = page.size() > pageLimit;
        List<AgentDispatchAttempt> items = hasMore ? page.subList(0, pageLimit) : page;
        String nextCursor = hasMore
                ? PageCursorCodec.encodeTimestamp(items.get(items.size() - 1).getStartedAt(), items.get(items.size() - 1).getId().toString())
                : null;
        return new CursorPageResponse<>(
                items.stream().map(AgentDispatchAttemptResponse::from).toList(),
                nextCursor,
                hasMore,
                pageLimit
        );
    }

    @Transactional
    public ExportJobResponse exportDispatchAttempts(UUID workspaceId, AgentDispatchAttemptExportRequest request) {
        AgentDispatchAttemptExportRequest exportRequest = request == null
                ? new AgentDispatchAttemptExportRequest(null, null, null, null, null, null, null, null, null)
                : request;
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.provider.manage");
        AgentDispatchAttemptFilter filter = dispatchAttemptFilter(
                exportRequest.agentTaskId(),
                exportRequest.providerId(),
                exportRequest.agentProfileId(),
                exportRequest.workItemId(),
                exportRequest.attemptType(),
                exportRequest.status(),
                exportRequest.startedFrom(),
                exportRequest.startedTo()
        );
        int exportLimit = normalizeListLimit(exportRequest.limit(), DEFAULT_DISPATCH_ATTEMPT_EXPORT_LIMIT, MAX_DISPATCH_ATTEMPT_EXPORT_LIMIT);
        List<AgentDispatchAttempt> attempts = agentDispatchAttemptRepository.findFilteredPage(
                workspaceId,
                filter.agentTaskId(),
                filter.providerId(),
                filter.agentProfileId(),
                filter.workItemId(),
                filter.attemptType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                null,
                null,
                null,
                exportLimit
        );
        return storeDispatchAttemptExport(workspaceId, actorId, filter, null, attempts);
    }

    @Transactional
    public AgentDispatchAttemptRetentionResponse pruneDispatchAttempts(UUID workspaceId, AgentDispatchAttemptRetentionRequest request) {
        AgentDispatchAttemptRetentionRequest retentionRequest = request == null
                ? new AgentDispatchAttemptRetentionRequest(null, null, null, null, null, null, null, null, null, null)
                : request;
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.provider.manage");
        AgentDispatchAttemptFilter filter = dispatchAttemptFilter(
                retentionRequest.agentTaskId(),
                retentionRequest.providerId(),
                retentionRequest.agentProfileId(),
                retentionRequest.workItemId(),
                retentionRequest.attemptType(),
                retentionRequest.status(),
                retentionRequest.startedFrom(),
                retentionRequest.startedTo()
        );
        int retentionDays = retentionRequest.retentionDays() == null ? 30 : retentionRequest.retentionDays();
        if (retentionDays < 1 || retentionDays > 3650) {
            throw badRequest("retentionDays must be between 1 and 3650");
        }
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        long eligible = agentDispatchAttemptRepository.countFiltered(
                workspaceId,
                filter.agentTaskId(),
                filter.providerId(),
                filter.agentProfileId(),
                filter.workItemId(),
                filter.attemptType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                cutoff
        );
        if (eligible > MAX_DISPATCH_ATTEMPT_EXPORT_LIMIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Too many dispatch attempts are eligible for a single retention prune export");
        }
        List<AgentDispatchAttempt> attempts = agentDispatchAttemptRepository.findFilteredPage(
                workspaceId,
                filter.agentTaskId(),
                filter.providerId(),
                filter.agentProfileId(),
                filter.workItemId(),
                filter.attemptType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                cutoff,
                null,
                null,
                MAX_DISPATCH_ATTEMPT_EXPORT_LIMIT
        );
        ExportJobResponse export = eligible > 0 && !Boolean.FALSE.equals(retentionRequest.exportBeforePrune())
                ? storeDispatchAttemptExport(workspaceId, actorId, filter, cutoff, attempts)
                : null;
        int pruned = agentDispatchAttemptRepository.deleteFilteredBefore(
                workspaceId,
                filter.agentTaskId(),
                filter.providerId(),
                filter.agentProfileId(),
                filter.workItemId(),
                filter.attemptType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                cutoff
        );
        ObjectNode payload = dispatchAttemptFilterPayload(filter)
                .put("workspaceId", workspaceId.toString())
                .put("cutoff", cutoff.toString())
                .put("attemptsEligible", eligible)
                .put("attemptsPruned", pruned)
                .put("actorUserId", actorId.toString());
        if (export != null) {
            payload.put("exportJobId", export.id().toString());
            payload.put("fileAttachmentId", export.fileAttachmentId().toString());
        }
        domainEventService.record(workspaceId, "agent_dispatch_attempt", workspaceId, "agent.dispatch_attempts.pruned", payload);
        return new AgentDispatchAttemptRetentionResponse(
                workspaceId,
                filter.agentTaskId(),
                filter.providerId(),
                filter.agentProfileId(),
                filter.workItemId(),
                filter.attemptType(),
                filter.status(),
                filter.startedFrom(),
                filter.startedTo(),
                cutoff,
                eligible,
                attempts.size(),
                pruned,
                export == null ? null : export.id(),
                export == null ? null : export.fileAttachmentId(),
                attempts.stream().map(AgentDispatchAttemptResponse::from).toList()
        );
    }

    @Transactional
    public AgentProviderResponse createProvider(UUID workspaceId, AgentProviderRequest request) {
        AgentProviderRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.provider.manage");

        String providerKey = normalizeKey(requiredText(createRequest.providerKey(), "providerKey"));
        if (agentProviderRepository.findByWorkspaceIdAndProviderKey(workspaceId, providerKey).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent provider key already exists in this workspace");
        }

        AgentProvider provider = new AgentProvider();
        provider.setWorkspaceId(workspaceId);
        provider.setProviderKey(providerKey);
        provider.setProviderType(normalizeProviderType(createRequest.providerType()));
        provider.setDisplayName(requiredText(createRequest.displayName(), "displayName"));
        provider.setDispatchMode(normalizeDispatchMode(createRequest.dispatchMode()));
        provider.setCallbackUrl(createRequest.callbackUrl());
        provider.setCapabilitySchema(toJson(createRequest.capabilitySchema()));
        provider.setConfig(toJson(createRequest.config()));
        provider.setEnabled(createRequest.enabled() == null || createRequest.enabled());
        AgentProvider saved = agentProviderRepository.saveAndFlush(provider);
        saved.setConfig(callbackJwtService.ensureProviderKeyPair(saved));
        adapter(saved.getProviderType()).validateProvider(saved);
        saved = agentProviderRepository.save(saved);
        syncWorkerWebhookConsumer(saved);
        recordWorkspaceEvent(workspaceId, "agent_provider", saved.getId(), "agent.provider.created", actorId, payloadForProvider(saved, actorId));
        return AgentProviderResponse.from(saved);
    }

    @Transactional
    public AgentProviderResponse updateProvider(UUID providerId, AgentProviderRequest request) {
        AgentProviderRequest updateRequest = required(request, "request");
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.manage");
        if (hasText(updateRequest.displayName())) {
            provider.setDisplayName(updateRequest.displayName().trim());
        }
        if (hasText(updateRequest.dispatchMode())) {
            provider.setDispatchMode(normalizeDispatchMode(updateRequest.dispatchMode()));
        }
        if (updateRequest.callbackUrl() != null) {
            provider.setCallbackUrl(updateRequest.callbackUrl());
        }
        if (updateRequest.capabilitySchema() != null) {
            provider.setCapabilitySchema(toJson(updateRequest.capabilitySchema()));
        }
        if (updateRequest.config() != null) {
            provider.setConfig(toJson(updateRequest.config()));
        }
        provider.setConfig(callbackJwtService.ensureProviderKeyPair(provider));
        if (updateRequest.enabled() != null) {
            provider.setEnabled(updateRequest.enabled());
        }
        adapter(provider.getProviderType()).validateProvider(provider);
        AgentProvider saved = agentProviderRepository.save(provider);
        syncWorkerWebhookConsumer(saved);
        recordWorkspaceEvent(saved.getWorkspaceId(), "agent_provider", saved.getId(), "agent.provider.updated", actorId, payloadForProvider(saved, actorId));
        return AgentProviderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AgentRuntimePreviewResponse previewRuntime(UUID providerId, AgentRuntimePreviewRequest request) {
        AgentRuntimePreviewRequest previewRequest = request == null ? new AgentRuntimePreviewRequest(null, null, null) : request;
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.manage");
        String action = normalizeRuntimePreviewAction(previewRequest.action());
        UUID taskId = UUID.randomUUID();
        UUID profileId = previewRequest.agentProfileId() == null ? new UUID(0L, 0L) : previewRequest.agentProfileId();
        if (previewRequest.agentProfileId() != null) {
            AgentProfile profile = agentProfileRepository.findByIdAndWorkspaceId(previewRequest.agentProfileId(), provider.getWorkspaceId())
                    .orElseThrow(() -> badRequest("Agent profile not found in provider workspace"));
            if (!provider.getId().equals(profile.getProviderId())) {
                throw badRequest("Agent profile does not use this provider");
            }
        }
        try {
            adapter(provider.getProviderType()).validateProvider(provider);
            ObjectNode payload;
            String runtimeMode;
            String transport;
            Boolean externalExecutionEnabled;
            if (List.of("codex", "claude_code").contains(provider.getProviderType())) {
                ExternalAgentRuntimeConfig runtime = ExternalAgentRuntimeConfig.from(objectMapper, provider.getProviderType(), provider);
                runtime.validate();
                runtimeMode = runtime.mode();
                transport = runtime.transport();
                externalExecutionEnabled = runtime.externalExecutionEnabled();
                payload = runtime.previewPayload(taskId, profileId, provider, action);
            } else {
                runtimeMode = "provider_native";
                transport = provider.getDispatchMode();
                externalExecutionEnabled = false;
                payload = objectMapper.createObjectNode()
                        .put("adapter", provider.getProviderType())
                        .put("protocolVersion", "trasck.agent-runtime-preview.v1")
                        .put("action", action)
                        .put("providerRuntime", runtimeMode)
                        .put("transport", firstText(provider.getDispatchMode(), "managed"))
                        .put("externalDispatch", false)
                        .put("externalExecutionEnabled", false)
                        .put("agentTaskId", taskId.toString())
                        .put("providerId", provider.getId().toString())
                        .put("providerKey", provider.getProviderKey())
                        .put("agentProfileId", profileId.toString());
            }
            if (previewRequest.workItemId() != null) {
                payload.put("workItemId", previewRequest.workItemId().toString());
            }
            return new AgentRuntimePreviewResponse(
                    provider.getId(),
                    provider.getProviderKey(),
                    provider.getProviderType(),
                    provider.getDispatchMode(),
                    runtimeMode,
                    transport,
                    externalExecutionEnabled,
                    true,
                    List.of(),
                    JsonValues.toJavaValue(payload)
            );
        } catch (RuntimeException ex) {
            return new AgentRuntimePreviewResponse(
                    provider.getId(),
                    provider.getProviderKey(),
                    provider.getProviderType(),
                    provider.getDispatchMode(),
                    null,
                    null,
                    null,
                    false,
                    List.of(firstText(ex.getMessage(), ex.getClass().getSimpleName())),
                    Map.of()
            );
        }
    }

    private void syncWorkerWebhookConsumer(AgentProvider provider) {
        if (!"generic_worker".equals(provider.getProviderType())) {
            return;
        }
        String consumerKey = workerWebhookConsumerKey(provider);
        EventConsumerConfig config = eventConsumerConfigRepository.findByConsumerKey(consumerKey)
                .orElseGet(EventConsumerConfig::new);
        config.setWorkspaceId(provider.getWorkspaceId());
        config.setConsumerKey(consumerKey);
        config.setConsumerType("agent_worker_webhook");
        config.setDisplayName(truncate(provider.getDisplayName() + " Worker Webhook Dispatch", 160));
        config.setEventTypes(objectMapper.createArrayNode().add("agent.worker.dispatch_requested"));
        config.setConfig(objectMapper.createObjectNode()
                .put("providerId", provider.getId().toString())
                .put("providerKey", provider.getProviderKey())
                .put("callbackUrl", firstText(provider.getCallbackUrl(), ""))
                .put("maxAttempts", workerWebhookMaxAttempts(provider))
                .put("deadLetterOnExhaustion", workerWebhookDeadLetterOnExhaustion(provider)));
        config.setEnabled(shouldPublishWorkerWebhookDispatch(provider));
        eventConsumerConfigRepository.save(config);
    }

    private String workerWebhookConsumerKey(AgentProvider provider) {
        return "agent-worker-webhook-" + provider.getId();
    }

    private boolean shouldPublishWorkerWebhookDispatch(AgentProvider provider) {
        return "generic_worker".equals(provider.getProviderType())
                && "webhook_push".equals(provider.getDispatchMode())
                && Boolean.TRUE.equals(provider.getEnabled())
                && hasText(provider.getCallbackUrl());
    }

    private int workerWebhookMaxAttempts(AgentProvider provider) {
        JsonNode webhookConfig = workerWebhookConfig(provider);
        if (webhookConfig.hasNonNull("maxAttempts")) {
            return Math.max(1, webhookConfig.path("maxAttempts").asInt(5));
        }
        JsonNode providerConfig = provider.getConfig();
        if (providerConfig != null && providerConfig.hasNonNull("workerWebhookMaxAttempts")) {
            return Math.max(1, providerConfig.path("workerWebhookMaxAttempts").asInt(5));
        }
        return 5;
    }

    private boolean workerWebhookDeadLetterOnExhaustion(AgentProvider provider) {
        JsonNode webhookConfig = workerWebhookConfig(provider);
        if (webhookConfig.hasNonNull("deadLetterOnExhaustion")) {
            return webhookConfig.path("deadLetterOnExhaustion").asBoolean(true);
        }
        JsonNode providerConfig = provider.getConfig();
        if (providerConfig != null && providerConfig.hasNonNull("workerWebhookDeadLetterOnExhaustion")) {
            return providerConfig.path("workerWebhookDeadLetterOnExhaustion").asBoolean(true);
        }
        return true;
    }

    private JsonNode workerWebhookConfig(AgentProvider provider) {
        JsonNode providerConfig = provider.getConfig();
        if (providerConfig == null || !providerConfig.path("workerWebhook").isObject()) {
            return objectMapper.createObjectNode();
        }
        return providerConfig.path("workerWebhook");
    }

    @Transactional
    public AgentProviderCredentialResponse createCredential(UUID providerId, AgentProviderCredentialRequest request) {
        AgentProviderCredentialRequest createRequest = required(request, "request");
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.credential.manage");
        String credentialType = normalizeKey(requiredText(createRequest.credentialType(), "credentialType"));
        JsonNode metadata = credentialMetadata(credentialType, createRequest.metadata());
        String workerId = "worker_token".equals(credentialType) ? workerIdFromMetadata(metadata) : null;
        agentProviderCredentialRepository.findByProviderIdAndCredentialTypeAndActiveTrue(providerId, credentialType).stream()
                .filter(existing -> workerId == null || workerId.equals(workerIdFromMetadata(existing.getMetadata())))
                .forEach(existing -> {
                    existing.setActive(false);
                    existing.setRotatedAt(OffsetDateTime.now());
                });
        AgentProviderCredential credential = new AgentProviderCredential();
        credential.setProviderId(providerId);
        credential.setCredentialType(credentialType);
        credential.setEncryptedSecret(secretCipherService.encrypt(requiredText(createRequest.secret(), "secret")));
        credential.setMetadata(metadata);
        credential.setActive(true);
        credential.setExpiresAt(createRequest.expiresAt());
        AgentProviderCredential saved = agentProviderCredentialRepository.save(credential);
        ObjectNode payload = payloadForProvider(provider, actorId)
                .put("credentialId", saved.getId().toString())
                .put("credentialType", saved.getCredentialType());
        recordWorkspaceEvent(provider.getWorkspaceId(), "agent_provider", provider.getId(), "agent.provider.credential_created", actorId, payload);
        return AgentProviderCredentialResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AgentProviderCredentialResponse> listCredentials(UUID providerId) {
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.credential.manage");
        return agentProviderCredentialRepository.findByProviderIdOrderByCreatedAtAsc(providerId).stream()
                .map(AgentProviderCredentialResponse::from)
                .toList();
    }

    @Transactional
    public AgentProviderCredentialResponse deactivateCredential(UUID providerId, UUID credentialId) {
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.credential.manage");
        AgentProviderCredential credential = agentProviderCredentialRepository.findByIdAndProviderId(credentialId, providerId)
                .orElseThrow(() -> notFound("Agent provider credential not found"));
        if ("callback_private_key".equals(credential.getCredentialType())) {
            throw badRequest("Callback signing keys must be rotated through the callback key rotation endpoint");
        }
        if (Boolean.TRUE.equals(credential.getActive())) {
            credential.setActive(false);
            credential.setRotatedAt(OffsetDateTime.now());
        }
        AgentProviderCredential saved = agentProviderCredentialRepository.save(credential);
        ObjectNode payload = payloadForProvider(provider, actorId)
                .put("credentialId", saved.getId().toString())
                .put("credentialType", saved.getCredentialType());
        recordWorkspaceEvent(provider.getWorkspaceId(), "agent_provider", provider.getId(), "agent.provider.credential_deactivated", actorId, payload);
        return AgentProviderCredentialResponse.from(saved);
    }

    @Transactional
    public List<AgentProviderCredentialResponse> reencryptCredentials(UUID providerId) {
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.credential.manage");
        List<AgentProviderCredential> credentials = agentProviderCredentialRepository.findByProviderIdOrderByCreatedAtAsc(providerId);
        for (AgentProviderCredential credential : credentials) {
            credential.setEncryptedSecret(secretCipherService.encrypt(secretCipherService.decrypt(credential.getEncryptedSecret())));
        }
        List<AgentProviderCredential> saved = agentProviderCredentialRepository.saveAll(credentials);
        ObjectNode payload = payloadForProvider(provider, actorId)
                .put("credentialCount", saved.size());
        recordWorkspaceEvent(provider.getWorkspaceId(), "agent_provider", provider.getId(), "agent.provider.credentials_reencrypted", actorId, payload);
        return saved.stream().map(AgentProviderCredentialResponse::from).toList();
    }

    @Transactional
    public AgentProviderResponse rotateCallbackKey(UUID providerId) {
        AgentProvider provider = agentProviderRepository.findById(providerId).orElseThrow(() -> notFound("Agent provider not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, provider.getWorkspaceId(), "agent.provider.credential.manage");
        provider.setConfig(callbackJwtService.rotateProviderKeyPair(provider));
        AgentProvider saved = agentProviderRepository.save(provider);
        recordWorkspaceEvent(saved.getWorkspaceId(), "agent_provider", saved.getId(), "agent.provider.callback_key_rotated", actorId, payloadForProvider(saved, actorId));
        return AgentProviderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AgentProfileResponse> listProfiles(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.profile.manage");
        return agentProfileRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(this::profileResponse)
                .toList();
    }

    @Transactional
    public AgentProfileResponse createProfile(UUID workspaceId, AgentProfileRequest request) {
        AgentProfileRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "agent.profile.manage");
        AgentProvider provider = agentProviderRepository.findByIdAndWorkspaceId(required(createRequest.providerId(), "providerId"), workspaceId)
                .orElseThrow(() -> notFound("Agent provider not found"));
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw badRequest("Agent provider is disabled");
        }
        Role role = resolveWorkspaceRole(workspaceId, createRequest.roleId());
        String displayName = requiredText(createRequest.displayName(), "displayName");
        User agentUser = createAgentUser(displayName, createRequest.username());
        createWorkspaceMembership(workspaceId, agentUser.getId(), role.getId());

        AgentProfile profile = new AgentProfile();
        profile.setWorkspaceId(workspaceId);
        profile.setUserId(agentUser.getId());
        profile.setProviderId(provider.getId());
        profile.setDisplayName(displayName);
        profile.setStatus(normalizeProfileStatus(createRequest.status()));
        profile.setMaxConcurrentTasks(normalizeMaxConcurrentTasks(createRequest.maxConcurrentTasks()));
        profile.setCapabilities(toJson(createRequest.capabilities()));
        profile.setConfig(toJson(createRequest.config()));
        AgentProfile saved = agentProfileRepository.save(profile);
        replaceProfileProjects(saved, createRequest.projectIds());
        recordWorkspaceEvent(workspaceId, "agent_profile", saved.getId(), "agent.profile.created", actorId, payloadForProfile(saved, actorId));
        return profileResponse(saved);
    }

    @Transactional
    public AgentProfileResponse updateProfile(UUID profileId, AgentProfileRequest request) {
        AgentProfileRequest updateRequest = required(request, "request");
        AgentProfile profile = agentProfileRepository.findById(profileId).orElseThrow(() -> notFound("Agent profile not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, profile.getWorkspaceId(), "agent.profile.manage");
        if (hasText(updateRequest.displayName())) {
            profile.setDisplayName(updateRequest.displayName().trim());
        }
        if (hasText(updateRequest.status())) {
            profile.setStatus(normalizeProfileStatus(updateRequest.status()));
        }
        if (updateRequest.maxConcurrentTasks() != null) {
            profile.setMaxConcurrentTasks(normalizeMaxConcurrentTasks(updateRequest.maxConcurrentTasks()));
        }
        if (updateRequest.capabilities() != null) {
            profile.setCapabilities(toJson(updateRequest.capabilities()));
        }
        if (updateRequest.config() != null) {
            profile.setConfig(toJson(updateRequest.config()));
        }
        AgentProfile saved = agentProfileRepository.save(profile);
        if (updateRequest.projectIds() != null) {
            replaceProfileProjects(saved, updateRequest.projectIds());
            recordWorkspaceEvent(saved.getWorkspaceId(), "agent_profile", saved.getId(), "agent.profile.project_scope_updated", actorId, payloadForProfile(saved, actorId));
        }
        recordWorkspaceEvent(saved.getWorkspaceId(), "agent_profile", saved.getId(), "agent.profile.updated", actorId, payloadForProfile(saved, actorId));
        return profileResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RepositoryConnectionResponse> listRepositoryConnections(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "repository_connection.manage");
        return repositoryConnectionRepository.findByWorkspaceIdAndActiveTrueOrderByCreatedAtAsc(workspaceId).stream()
                .map(RepositoryConnectionResponse::from)
                .toList();
    }

    @Transactional
    public RepositoryConnectionResponse createRepositoryConnection(UUID workspaceId, RepositoryConnectionRequest request) {
        RepositoryConnectionRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "repository_connection.manage");
        if (createRequest.projectId() != null) {
            Project project = activeProject(createRequest.projectId());
            if (!workspaceId.equals(project.getWorkspaceId())) {
                throw badRequest("Repository project must belong to the workspace");
            }
        }
        RepositoryConnection connection = new RepositoryConnection();
        connection.setWorkspaceId(workspaceId);
        connection.setProjectId(createRequest.projectId());
        connection.setProvider(normalizeRepositoryProvider(createRequest.provider()));
        connection.setName(requiredText(createRequest.name(), "name"));
        connection.setRepositoryUrl(requiredText(createRequest.repositoryUrl(), "repositoryUrl"));
        connection.setDefaultBranch(hasText(createRequest.defaultBranch()) ? createRequest.defaultBranch().trim() : "main");
        connection.setConfig(repositoryConfig(createRequest));
        connection.setActive(createRequest.active() == null || createRequest.active());
        RepositoryConnection saved = repositoryConnectionRepository.save(connection);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("repositoryConnectionId", saved.getId().toString())
                .put("provider", saved.getProvider())
                .put("repositoryUrl", saved.getRepositoryUrl())
                .put("actorUserId", actorId.toString());
        recordWorkspaceEvent(workspaceId, "repository_connection", saved.getId(), "repository_connection.created", actorId, payload);
        return RepositoryConnectionResponse.from(saved);
    }

    @Transactional
    public AgentTaskResponse assignAgent(UUID workItemId, AgentTaskAssignRequest request) {
        AgentTaskAssignRequest assignRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "agent.assign");
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.update");
        AgentProfile profile = activeProfile(item.getWorkspaceId(), required(assignRequest.agentProfileId(), "agentProfileId"));
        AgentProvider provider = activeProvider(item.getWorkspaceId(), profile.getProviderId());
        requireAgentAvailableForProject(profile, item);
        requireAgentProjectPermission(profile, item, "work_item.read", "Agent profile cannot access this work item");
        assertAgentCapacity(profile);

        UUID previousAssignee = item.getAssigneeId();
        item.setAssigneeId(profile.getUserId());
        item.setUpdatedById(actorId);
        workItemRepository.saveAndFlush(item);
        writeAssignmentHistory(item.getId(), previousAssignee, profile.getUserId(), actorId);

        AgentTask task = new AgentTask();
        task.setWorkspaceId(item.getWorkspaceId());
        task.setWorkItemId(item.getId());
        task.setAgentProfileId(profile.getId());
        task.setProviderId(provider.getId());
        task.setRequestedById(actorId);
        task.setStatus("queued");
        task.setDispatchMode(provider.getDispatchMode());
        task.setContextSnapshot(contextSnapshot(item, profile, provider));
        task.setRequestPayload(toJson(assignRequest.requestPayload()));
        task.setQueuedAt(OffsetDateTime.now());
        AgentTask saved = agentTaskRepository.saveAndFlush(task);
        linkRepositories(saved, item, assignRequest.repositoryConnectionIds());
        appendTaskEvent(saved, "queued", "info", "Agent task queued.", objectMapper.createObjectNode());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.created", actorId, null);
        recordWorkItemAgentEvent(item, "work_item.agent_assigned", actorId, saved, profile);
        String callbackToken = dispatch(saved, item, provider, profile, actorId, false);
        return response(saved, callbackToken);
    }

    @Transactional(readOnly = true)
    public AgentTaskResponse getTask(UUID taskId) {
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.view");
        return response(task, null);
    }

    @Transactional
    public AgentTaskResponse addHumanMessage(UUID taskId, AgentTaskHumanMessageRequest request) {
        AgentTaskHumanMessageRequest messageRequest = required(request, "request");
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.view");
        saveHumanMessage(task, actorId, requiredText(messageRequest.bodyMarkdown(), "bodyMarkdown"), messageRequest.bodyDocument());
        String eventType = "human_message_added";
        if ("waiting_for_input".equals(task.getStatus())) {
            task.setStatus("running");
            eventType = "input_provided";
        }
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, eventType, "info", "Human message added to agent task.", actorPayload(actorId));
        WorkItem item = activeWorkItem(saved.getWorkItemId());
        AgentProfile profile = activeProfile(saved.getWorkspaceId(), saved.getAgentProfileId());
        AgentProvider provider = activeProvider(saved.getWorkspaceId(), saved.getProviderId());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task." + eventType, actorId, messageRequest.bodyMarkdown());
        return response(saved, null);
    }

    @Transactional
    public AgentTaskResponse requestChanges(UUID taskId, AgentTaskRequestChangesRequest request) {
        AgentTaskRequestChangesRequest changesRequest = required(request, "request");
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.accept_result");
        if (!List.of("review_requested", "waiting_for_input", "running").contains(task.getStatus())) {
            throw badRequest("Changes can only be requested for running, waiting, or review-requested agent tasks");
        }
        String message = requiredText(changesRequest.message(), "message");
        saveHumanMessage(task, actorId, message, null);
        if (changesRequest.requestPayload() != null) {
            task.setRequestPayload(toJson(changesRequest.requestPayload()));
        }
        task.setStatus("waiting_for_input");
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, "changes_requested", "warning", message, actorPayload(actorId));
        WorkItem item = activeWorkItem(saved.getWorkItemId());
        AgentProfile profile = activeProfile(saved.getWorkspaceId(), saved.getAgentProfileId());
        AgentProvider provider = activeProvider(saved.getWorkspaceId(), saved.getProviderId());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.changes_requested", actorId, message);
        return response(saved, null);
    }

    @Transactional
    public AgentTaskResponse cancelTask(UUID taskId) {
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.cancel");
        if (isTerminal(task.getStatus())) {
            return response(task, null);
        }
        AgentProfile profile = activeProfile(task.getWorkspaceId(), task.getAgentProfileId());
        AgentProvider provider = activeProvider(task.getWorkspaceId(), task.getProviderId());
        boolean cancelDispatched = recordCancelAttempt(task, provider, profile, actorId);
        if (!cancelDispatched) {
            WorkItem item = activeWorkItem(task.getWorkItemId());
            recordAgentTaskEvent(task, item, provider, profile, "agent.task.cancel_failed", actorId, null);
            return response(task, null);
        }
        task.setStatus("canceled");
        task.setCanceledAt(OffsetDateTime.now());
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, "canceled", "warning", "Agent task canceled.", actorPayload(actorId));
        WorkItem item = activeWorkItem(saved.getWorkItemId());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.canceled", actorId, null);
        return response(saved, null);
    }

    @Transactional
    public AgentTaskResponse retryTask(UUID taskId) {
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.retry");
        if (!List.of("failed", "canceled").contains(task.getStatus())) {
            throw badRequest("Only failed or canceled agent tasks can be retried");
        }
        AgentProfile profile = activeProfile(task.getWorkspaceId(), task.getAgentProfileId());
        AgentProvider provider = activeProvider(task.getWorkspaceId(), task.getProviderId());
        assertAgentCapacity(profile);
        task.setStatus("queued");
        task.setQueuedAt(OffsetDateTime.now());
        task.setStartedAt(null);
        task.setFailedAt(null);
        task.setCanceledAt(null);
        task.setCompletedAt(null);
        task.setResultPayload(null);
        AgentTask saved = agentTaskRepository.saveAndFlush(task);
        appendTaskEvent(saved, "retried", "info", "Agent task queued for retry.", actorPayload(actorId));
        WorkItem item = activeWorkItem(saved.getWorkItemId());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.retried", actorId, null);
        String callbackToken = dispatch(saved, item, provider, profile, actorId, true);
        return response(saved, callbackToken);
    }

    @Transactional
    public AgentTaskResponse acceptResult(UUID taskId) {
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.accept_result");
        if (!"review_requested".equals(task.getStatus())) {
            throw badRequest("Only agent tasks awaiting review can be accepted");
        }
        task.setStatus("completed");
        task.setCompletedAt(OffsetDateTime.now());
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, "completed", "info", "Agent task result accepted.", actorPayload(actorId));
        WorkItem item = activeWorkItem(saved.getWorkItemId());
        AgentProfile profile = activeProfile(saved.getWorkspaceId(), saved.getAgentProfileId());
        AgentProvider provider = activeProvider(saved.getWorkspaceId(), saved.getProviderId());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.completed", actorId, null);
        transitionAcceptedWorkToApproval(saved, item, actorId);
        return response(saved, null);
    }

    @Transactional
    public AgentTaskResponse handleCallback(String providerKey, String assertion, AgentTaskCallbackRequest request) {
        AgentTaskCallbackRequest callback = required(request, "request");
        AgentCallbackJwtService.AgentCallbackClaims untrustedClaims = callbackJwtService.peek(assertion);
        if (!normalizeKey(providerKey).equals(untrustedClaims.providerKey())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent callback provider mismatch");
        }
        AgentTask task = agentTaskRepository.findByIdAndWorkspaceId(untrustedClaims.taskId(), untrustedClaims.workspaceId())
                .orElseThrow(() -> notFound("Agent task not found"));
        AgentProvider provider = activeProvider(task.getWorkspaceId(), task.getProviderId());
        AgentCallbackJwtService.AgentCallbackClaims claims = callbackJwtService.parse(provider, assertion);
        if (!normalizeKey(providerKey).equals(claims.providerKey())
                || !claims.taskId().equals(task.getId())
                || !claims.workspaceId().equals(task.getWorkspaceId())
                || !claims.providerId().equals(task.getProviderId())
                || !claims.agentProfileId().equals(task.getAgentProfileId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Agent callback task mismatch");
        }
        AgentProfile profile = activeProfile(task.getWorkspaceId(), task.getAgentProfileId());
        WorkItem item = activeWorkItem(task.getWorkItemId());
        String status = normalizeCallbackStatus(callback.status());
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("jwtId", claims.jwtId() == null ? "" : claims.jwtId())
                .put("callbackStatus", status);
        appendTaskEvent(task, "callback_received", "info", callback.message(), metadata);
        if ("running".equals(status)) {
            task.setStatus("running");
            if (task.getStartedAt() == null) {
                task.setStartedAt(OffsetDateTime.now());
            }
            return response(agentTaskRepository.save(task), null);
        }
        if ("waiting_for_input".equals(status)) {
            task.setStatus("waiting_for_input");
            saveCallbackMessages(task, profile, callback);
            AgentTask saved = agentTaskRepository.save(task);
            recordAgentTaskEvent(saved, item, provider, profile, "agent.task.waiting_for_input", profile.getUserId(), callback.message());
            return response(saved, null);
        }
        if ("failed".equals(status)) {
            task.setStatus("failed");
            task.setFailedAt(OffsetDateTime.now());
            task.setResultPayload(toJson(callback.resultPayload()));
            AgentTask saved = agentTaskRepository.save(task);
            appendTaskEvent(saved, "failed", "error", callback.message(), objectMapper.createObjectNode());
            recordAgentTaskEvent(saved, item, provider, profile, "agent.task.failed", profile.getUserId(), callback.message());
            return response(saved, null);
        }
        return completeForReview(task, provider, profile, item, callback);
    }

    @Transactional
    public Optional<AgentWorkerTaskResponse> claimWorkerTask(UUID workspaceId, String providerKey, String workerToken, AgentWorkerClaimRequest request) {
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, request == null ? null : request.workerId());
        AgentProvider provider = worker.provider();
        Optional<AgentTask> task = agentTaskRepository.findFirstByWorkspaceIdAndProviderIdAndStatusInOrderByQueuedAtAsc(
                workspaceId,
                provider.getId(),
                ACTIVE_TASK_STATUSES
        );
        if (task.isEmpty()) {
            return Optional.empty();
        }
        AgentTask claimed = task.get();
        appendTaskEvent(claimed, "worker_claimed", "info", "Worker claimed agent task.", workerMetadata(worker.workerId(), request == null ? null : request.metadata()));
        AgentProfile profile = activeProfile(claimed.getWorkspaceId(), claimed.getAgentProfileId());
        return Optional.of(workerTaskResponse(claimed, provider, profile, "polling"));
    }

    @Transactional
    public AgentWorkerTaskResponse workerDispatch(UUID taskId) {
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow(() -> notFound("Agent task not found"));
        UUID actorId = currentUserService.requireUserId();
        permissionService.requireWorkspacePermission(actorId, task.getWorkspaceId(), "agent.task.view");
        AgentProvider provider = activeProvider(task.getWorkspaceId(), task.getProviderId());
        AgentProfile profile = activeProfile(task.getWorkspaceId(), task.getAgentProfileId());
        if (!"generic_worker".equals(provider.getProviderType())) {
            throw badRequest("Worker dispatch payloads are only available for generic worker providers");
        }
        appendTaskEvent(task, "worker_dispatch_payload_created", "info", "Worker dispatch payload created.", actorPayload(actorId));
        return workerTaskResponse(task, provider, profile, "webhook_push");
    }

    @Transactional
    public AgentTaskResponse workerHeartbeat(UUID workspaceId, String providerKey, UUID taskId, String workerToken, AgentWorkerHeartbeatRequest request) {
        AgentWorkerHeartbeatRequest heartbeat = required(request, "request");
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, heartbeat.workerId());
        AgentProvider provider = worker.provider();
        AgentTask task = workerTask(provider, taskId);
        if (hasText(heartbeat.status())) {
            String status = normalizeCallbackStatus(heartbeat.status());
            if (!List.of("running", "waiting_for_input").contains(status)) {
                throw badRequest("Worker heartbeat status must be running or waiting_for_input");
            }
            task.setStatus(status);
            if ("running".equals(status) && task.getStartedAt() == null) {
                task.setStartedAt(OffsetDateTime.now());
            }
        }
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, "worker_heartbeat", "info", firstText(heartbeat.message(), "Worker heartbeat received."), workerMetadata(worker.workerId(), heartbeat.metadata()));
        return response(saved, null);
    }

    @Transactional
    public AgentTaskResponse workerLog(UUID workspaceId, String providerKey, UUID taskId, String workerToken, AgentWorkerEventRequest request) {
        AgentWorkerEventRequest eventRequest = required(request, "request");
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, eventRequest.workerId());
        AgentProvider provider = worker.provider();
        AgentTask task = workerTask(provider, taskId);
        appendTaskEvent(
                task,
                normalizeKey(firstText(eventRequest.eventType(), "worker_log")),
                normalizeSeverity(eventRequest.severity()),
                eventRequest.message(),
                workerMetadata(worker.workerId(), eventRequest.metadata())
        );
        return response(task, null);
    }

    @Transactional
    public AgentTaskResponse workerMessage(UUID workspaceId, String providerKey, UUID taskId, String workerToken, AgentTaskCallbackMessageRequest request) {
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, null);
        AgentProvider provider = worker.provider();
        AgentTask task = workerTask(provider, taskId);
        AgentProfile profile = activeProfile(task.getWorkspaceId(), task.getAgentProfileId());
        AgentTaskCallbackMessageRequest messageRequest = required(request, "request");
        saveAgentMessage(task, profile, normalizeSenderType(messageRequest.senderType()), requiredText(messageRequest.bodyMarkdown(), "bodyMarkdown"), messageRequest.bodyDocument());
        appendTaskEvent(task, "worker_message_added", "info", "Worker message added.", workerMetadata(worker.workerId(), null));
        return response(task, null);
    }

    @Transactional
    public AgentTaskResponse workerArtifact(UUID workspaceId, String providerKey, UUID taskId, String workerToken, AgentTaskCallbackArtifactRequest request) {
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, null);
        AgentProvider provider = worker.provider();
        AgentTask task = workerTask(provider, taskId);
        saveArtifact(task, required(request, "request"));
        appendTaskEvent(task, "worker_artifact_added", "info", "Worker artifact added.", workerMetadata(worker.workerId(), null));
        return response(task, null);
    }

    @Transactional
    public AgentTaskResponse workerCancel(UUID workspaceId, String providerKey, UUID taskId, String workerToken, AgentWorkerEventRequest request) {
        AgentWorkerEventRequest eventRequest = request == null ? new AgentWorkerEventRequest(null, null, null, null, null) : request;
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, eventRequest.workerId());
        AgentProvider provider = worker.provider();
        AgentTask task = workerTask(provider, taskId);
        if (!isTerminal(task.getStatus())) {
            task.setStatus("canceled");
            task.setCanceledAt(OffsetDateTime.now());
        }
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, "worker_cancel_acknowledged", "warning", firstText(eventRequest.message(), "Worker acknowledged cancellation."), workerMetadata(worker.workerId(), eventRequest.metadata()));
        WorkItem item = activeWorkItem(saved.getWorkItemId());
        AgentProfile profile = activeProfile(saved.getWorkspaceId(), saved.getAgentProfileId());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.canceled", profile.getUserId(), eventRequest.message());
        return response(saved, null);
    }

    @Transactional
    public AgentWorkerTaskResponse workerRetry(UUID workspaceId, String providerKey, UUID taskId, String workerToken, AgentWorkerEventRequest request) {
        AgentWorkerEventRequest eventRequest = request == null ? new AgentWorkerEventRequest(null, null, null, null, null) : request;
        AuthenticatedWorker worker = authenticatedWorker(workspaceId, providerKey, workerToken, eventRequest.workerId());
        AgentProvider provider = worker.provider();
        AgentTask task = workerTask(provider, taskId);
        if (List.of("failed", "canceled").contains(task.getStatus())) {
            task.setStatus("running");
            task.setStartedAt(OffsetDateTime.now());
            task.setFailedAt(null);
            task.setCanceledAt(null);
            task.setCompletedAt(null);
            task.setResultPayload(null);
        }
        AgentTask saved = agentTaskRepository.save(task);
        appendTaskEvent(saved, "worker_retry_started", "info", firstText(eventRequest.message(), "Worker retry started."), workerMetadata(worker.workerId(), eventRequest.metadata()));
        AgentProfile profile = activeProfile(saved.getWorkspaceId(), saved.getAgentProfileId());
        return workerTaskResponse(saved, provider, profile, "polling");
    }

    private AgentTaskResponse completeForReview(AgentTask task, AgentProvider provider, AgentProfile profile, WorkItem item, AgentTaskCallbackRequest callback) {
        if (List.of("review_requested", "completed").contains(task.getStatus())) {
            return response(task, null);
        }
        task.setStatus("review_requested");
        task.setResultPayload(toJson(callback.resultPayload()));
        AgentTask saved = agentTaskRepository.save(task);
        saveCallbackMessages(saved, profile, callback);
        saveCallbackArtifacts(saved, callback);
        createReviewArtifact(saved, callback);
        createReviewComment(item, profile, saved, callback);
        appendTaskEvent(saved, "review_requested", "info", firstText(callback.message(), "Agent requested human review."), objectMapper.createObjectNode());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.review_requested", profile.getUserId(), callback.message());
        recordWorkItemAgentEvent(item, "work_item.agent_review_requested", profile.getUserId(), saved, profile);
        return response(saved, null);
    }

    private String dispatch(AgentTask task, WorkItem item, AgentProvider provider, AgentProfile profile, UUID actorId, boolean retry) {
        provider.setConfig(callbackJwtService.ensureProviderKeyPair(provider));
        agentProviderRepository.save(provider);
        String attemptType = retry ? "retry" : "dispatch";
        OffsetDateTime startedAt = OffsetDateTime.now();
        AgentDispatchResult result;
        try {
            result = retry
                    ? adapter(provider.getProviderType()).retry(task, provider, profile)
                    : adapter(provider.getProviderType()).dispatch(task, provider, profile);
        } catch (RuntimeException ex) {
            task.setStatus("failed");
            task.setFailedAt(OffsetDateTime.now());
            AgentTask failed = agentTaskRepository.save(task);
            String message = firstText(ex.getMessage(), ex.getClass().getSimpleName());
            persistDispatchAttempt(failed, provider, profile, actorId, attemptType, "failed", null, null, message, startedAt);
            appendTaskEvent(failed, attemptType + "_failed", "error", "Agent task " + attemptType + " failed at provider boundary.", objectMapper.createObjectNode()
                    .put("errorMessage", truncate(message, 2_000)));
            recordAgentTaskEvent(failed, item, provider, profile, retry ? "agent.task.retry_failed" : "agent.task.dispatch_failed", actorId, message);
            return null;
        }
        task.setExternalTaskId(result.externalTaskId());
        task.setStatus("running");
        task.setStartedAt(OffsetDateTime.now());
        AgentTask saved = agentTaskRepository.save(task);
        persistDispatchAttempt(saved, provider, profile, actorId, attemptType, "succeeded", result.externalTaskId(), result.dispatchPayload(), null, startedAt);
        appendTaskEvent(saved, "running", "info", "Agent task dispatched.", result.dispatchPayload());
        recordAgentTaskEvent(saved, item, provider, profile, "agent.task.dispatched", actorId, null);
        String callbackToken = callbackJwtService.issue(saved, provider, profile);
        if (shouldPublishWorkerWebhookDispatch(provider)) {
            recordWorkerWebhookDispatchRequested(saved, item, provider, profile, result.dispatchPayload(), actorId, retry);
        }
        return callbackToken;
    }

    private boolean recordCancelAttempt(AgentTask task, AgentProvider provider, AgentProfile profile, UUID actorId) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        try {
            adapter(provider.getProviderType()).cancel(task, provider, profile);
            persistDispatchAttempt(task, provider, profile, actorId, "cancel", "succeeded", task.getExternalTaskId(), objectMapper.createObjectNode()
                    .put("adapter", provider.getProviderType())
                    .put("action", "cancel")
                    .put("agentTaskId", task.getId().toString())
                    .put("providerId", provider.getId().toString())
                    .put("transport", firstText(task.getDispatchMode(), provider.getDispatchMode()))
                    .put("idempotencyKey", provider.getProviderType() + ":" + task.getId() + ":cancel"), null, startedAt);
            return true;
        } catch (RuntimeException ex) {
            String message = firstText(ex.getMessage(), ex.getClass().getSimpleName());
            persistDispatchAttempt(task, provider, profile, actorId, "cancel", "failed", task.getExternalTaskId(), null, message, startedAt);
            appendTaskEvent(task, "cancel_failed", "error", "Agent task cancellation failed at provider boundary.", objectMapper.createObjectNode()
                    .put("errorMessage", truncate(message, 2_000)));
            return false;
        }
    }

    private void persistDispatchAttempt(
            AgentTask task,
            AgentProvider provider,
            AgentProfile profile,
            UUID actorId,
            String attemptType,
            String status,
            String externalTaskId,
            JsonNode payload,
            String errorMessage,
            OffsetDateTime startedAt
    ) {
        JsonNode requestPayload = payload == null
                ? objectMapper.createObjectNode()
                        .put("adapter", provider.getProviderType())
                        .put("action", attemptType)
                        .put("agentTaskId", task.getId().toString())
                        .put("providerId", provider.getId().toString())
                        .put("agentProfileId", profile.getId().toString())
                        .put("transport", firstText(task.getDispatchMode(), provider.getDispatchMode()))
                        .put("idempotencyKey", provider.getProviderType() + ":" + task.getId() + ":" + attemptType)
                : payload;
        ObjectNode responsePayload = objectMapper.createObjectNode()
                .put("status", status);
        if (hasText(externalTaskId)) {
            responsePayload.put("externalTaskId", externalTaskId);
        }
        if (hasText(errorMessage)) {
            responsePayload.put("errorMessage", truncate(errorMessage, 2_000));
        }
        AgentDispatchAttempt attempt = new AgentDispatchAttempt();
        attempt.setWorkspaceId(task.getWorkspaceId());
        attempt.setAgentTaskId(task.getId());
        attempt.setProviderId(provider.getId());
        attempt.setAgentProfileId(profile.getId());
        attempt.setWorkItemId(task.getWorkItemId());
        attempt.setRequestedById(actorId);
        attempt.setAttemptType(attemptType);
        attempt.setDispatchMode(firstText(firstText(task.getDispatchMode(), provider.getDispatchMode()), "managed"));
        attempt.setProviderType(provider.getProviderType());
        attempt.setTransport(firstText(firstText(textAt(requestPayload, "transport"), textAt(requestPayload, "dispatchMode")), provider.getDispatchMode()));
        attempt.setStatus(status);
        attempt.setExternalTaskId(externalTaskId);
        attempt.setIdempotencyKey(textAt(requestPayload, "idempotencyKey"));
        attempt.setExternalDispatch(requestPayload.path("externalDispatch").asBoolean(false));
        attempt.setRequestPayload(requestPayload);
        attempt.setResponsePayload(responsePayload);
        attempt.setErrorMessage(hasText(errorMessage) ? truncate(errorMessage, 2_000) : null);
        attempt.setStartedAt(startedAt);
        attempt.setFinishedAt(OffsetDateTime.now());
        agentDispatchAttemptRepository.save(attempt);
    }

    private ExportJobResponse storeDispatchAttemptExport(
            UUID workspaceId,
            UUID actorId,
            AgentDispatchAttemptFilter filter,
            OffsetDateTime retentionCutoff,
            List<AgentDispatchAttempt> attempts
    ) {
        AttachmentStorageConfig storageConfig = attachmentStorageConfigRepository.findFirstByWorkspaceIdAndActiveTrueAndDefaultConfigTrue(workspaceId)
                .orElseThrow(() -> badRequest("Default attachment storage config not found"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        byte[] content = dispatchAttemptExportContent(workspaceId, actorId, filter, retentionCutoff, attempts, now);
        String filename = "agent-dispatch-attempts-" + workspaceId + dispatchAttemptExportFilenameSuffix(filter) + "-" + now.format(EXPORT_FILENAME_TIME) + ".json";
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
            exportJob.setExportType("agent_dispatch_attempts");
            exportJob.setStatus("completed");
            exportJob.setFileAttachmentId(savedAttachment.getId());
            exportJob.setRequestPayload(dispatchAttemptFilterPayload(filter));
            exportJob.setCreatedAt(now);
            exportJob.setStartedAt(now);
            exportJob.setFinishedAt(now);
            ExportJob savedExportJob = exportJobRepository.save(exportJob);
            ObjectNode payload = dispatchAttemptFilterPayload(filter)
                    .put("workspaceId", workspaceId.toString())
                    .put("exportJobId", savedExportJob.getId().toString())
                    .put("fileAttachmentId", savedAttachment.getId().toString())
                    .put("attemptsIncluded", attempts.size())
                    .put("actorUserId", actorId.toString());
            if (retentionCutoff != null) {
                payload.put("retentionCutoff", retentionCutoff.toString());
            }
            domainEventService.record(workspaceId, "agent_dispatch_attempt", workspaceId, "agent.dispatch_attempts.exported", payload);
            return ExportJobResponse.from(savedExportJob, savedAttachment);
        } catch (RuntimeException ex) {
            attachmentStorageService.delete(storageConfig, stored.storageKey());
            throw ex;
        }
    }

    private byte[] dispatchAttemptExportContent(
            UUID workspaceId,
            UUID actorId,
            AgentDispatchAttemptFilter filter,
            OffsetDateTime retentionCutoff,
            List<AgentDispatchAttempt> attempts,
            OffsetDateTime exportedAt
    ) {
        ObjectNode document = dispatchAttemptFilterPayload(filter)
                .put("workspaceId", workspaceId.toString())
                .put("exportedAt", exportedAt.toString())
                .put("attemptsIncluded", attempts.size());
        if (actorId != null) {
            document.put("actorUserId", actorId.toString());
        }
        if (retentionCutoff != null) {
            document.put("retentionCutoff", retentionCutoff.toString());
        }
        ArrayNode rows = objectMapper.createArrayNode();
        attempts.stream()
                .map(AgentDispatchAttemptResponse::from)
                .map(attempt -> (JsonNode) objectMapper.valueToTree(attempt))
                .forEach(rows::add);
        document.set("attempts", rows);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize agent dispatch attempt export", ex);
        }
    }

    private String dispatchAttemptExportFilenameSuffix(AgentDispatchAttemptFilter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.attemptType() != null) {
            parts.add(filter.attemptType());
        }
        if (filter.status() != null) {
            parts.add(filter.status());
        }
        return parts.isEmpty() ? "" : "-" + String.join("-", parts);
    }

    private void linkRepositories(AgentTask task, WorkItem item, List<UUID> repositoryConnectionIds) {
        if (repositoryConnectionIds == null || repositoryConnectionIds.isEmpty()) {
            return;
        }
        for (UUID repositoryConnectionId : repositoryConnectionIds.stream().distinct().toList()) {
            RepositoryConnection connection = repositoryConnectionRepository.findByIdAndWorkspaceIdAndActiveTrue(repositoryConnectionId, task.getWorkspaceId())
                    .orElseThrow(() -> notFound("Repository connection not found"));
            if (connection.getProjectId() != null && !connection.getProjectId().equals(item.getProjectId())) {
                throw badRequest("Repository connection is not available for this project");
            }
            AgentTaskRepositoryLink link = new AgentTaskRepositoryLink();
            link.setAgentTaskId(task.getId());
            link.setRepositoryConnectionId(connection.getId());
            link.setBaseBranch(connection.getDefaultBranch());
            link.setWorkingBranch("trasck/agent-" + task.getId());
            link.setMetadata(objectMapper.createObjectNode()
                    .put("provider", connection.getProvider())
                    .put("repositoryUrl", connection.getRepositoryUrl()));
            agentTaskRepositoryLinkRepository.save(link);
        }
    }

    private void saveCallbackMessages(AgentTask task, AgentProfile profile, AgentTaskCallbackRequest callback) {
        if (callback.messages() == null) {
            return;
        }
        for (AgentTaskCallbackMessageRequest request : callback.messages()) {
            saveAgentMessage(task, profile, normalizeSenderType(request.senderType()), request.bodyMarkdown(), request.bodyDocument());
        }
    }

    private void saveCallbackArtifacts(AgentTask task, AgentTaskCallbackRequest callback) {
        if (callback.artifacts() == null) {
            return;
        }
        for (AgentTaskCallbackArtifactRequest request : callback.artifacts()) {
            saveArtifact(task, request);
        }
    }

    private void saveHumanMessage(AgentTask task, UUID actorId, String bodyMarkdown, Object bodyDocument) {
        AgentMessage message = new AgentMessage();
        message.setAgentTaskId(task.getId());
        message.setSenderUserId(actorId);
        message.setSenderType("human");
        message.setBodyMarkdown(bodyMarkdown);
        message.setBodyDocument(toJson(bodyDocument));
        agentMessageRepository.save(message);
    }

    private void saveAgentMessage(AgentTask task, AgentProfile profile, String senderType, String bodyMarkdown, Object bodyDocument) {
        AgentMessage message = new AgentMessage();
        message.setAgentTaskId(task.getId());
        message.setSenderUserId(profile.getUserId());
        message.setSenderType(senderType);
        message.setBodyMarkdown(bodyMarkdown);
        message.setBodyDocument(toJson(bodyDocument));
        agentMessageRepository.save(message);
    }

    private void saveArtifact(AgentTask task, AgentTaskCallbackArtifactRequest request) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setAgentTaskId(task.getId());
        artifact.setArtifactType(normalizeKey(firstText(request.artifactType(), "artifact")));
        artifact.setName(requiredText(request.name(), "artifact.name"));
        artifact.setExternalUrl(request.externalUrl());
        artifact.setMetadata(toJson(request.metadata()));
        agentArtifactRepository.save(artifact);
    }

    private void createReviewArtifact(AgentTask task, AgentTaskCallbackRequest callback) {
        if (agentArtifactRepository.existsByAgentTaskIdAndArtifactTypeAndName(task.getId(), "review", "Agent Review Request")) {
            return;
        }
        AgentArtifact artifact = new AgentArtifact();
        artifact.setAgentTaskId(task.getId());
        artifact.setArtifactType("review");
        artifact.setName("Agent Review Request");
        artifact.setMetadata(objectMapper.createObjectNode()
                .put("message", firstText(callback.message(), "Agent requested human review.")));
        agentArtifactRepository.save(artifact);
    }

    private void createReviewComment(WorkItem item, AgentProfile profile, AgentTask task, AgentTaskCallbackRequest callback) {
        requireAgentProjectPermission(profile, item, "work_item.comment", "Agent profile cannot comment on this work item");
        Comment comment = new Comment();
        comment.setWorkItemId(item.getId());
        comment.setAuthorId(profile.getUserId());
        comment.setBodyMarkdown(firstText(callback.message(), "Agent requested human review for task " + task.getId() + "."));
        comment.setBodyDocument(objectMapper.createObjectNode()
                .put("type", "doc")
                .put("source", "agent")
                .put("agentTaskId", task.getId().toString())
                .put("text", firstText(callback.message(), "Agent requested human review.")));
        comment.setVisibility("workspace");
        Comment saved = commentRepository.save(comment);
        ObjectNode payload = workItemPayload(item, profile.getUserId())
                .put("commentId", saved.getId().toString())
                .put("agentTaskId", task.getId().toString());
        domainEventService.record(item.getWorkspaceId(), "work_item", item.getId(), "work_item.comment_created", payload);
    }

    private AgentTaskResponse response(AgentTask task, String callbackToken) {
        return AgentTaskResponse.from(
                task,
                agentTaskEventRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId()),
                agentMessageRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId()),
                agentArtifactRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId()),
                agentTaskRepositoryLinkRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId()),
                agentDispatchAttemptRepository.findByAgentTaskIdOrderByStartedAtAscIdAsc(task.getId()),
                callbackToken
        );
    }

    private AgentWorkerTaskResponse workerTaskResponse(AgentTask task, AgentProvider provider, AgentProfile profile, String transport) {
        return AgentWorkerTaskResponse.from(
                task,
                provider,
                agentTaskRepositoryLinkRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId()),
                transport,
                workerEndpoints(task, provider),
                callbackJwtService.issue(task, provider, profile)
        );
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

    private AgentProfileResponse profileResponse(AgentProfile profile) {
        List<UUID> projectIds = agentProfileProjectRepository.findByIdAgentProfileIdOrderByCreatedAtAsc(profile.getId()).stream()
                .map(scope -> scope.getId().getProjectId())
                .toList();
        return AgentProfileResponse.from(profile, projectIds);
    }

    private void replaceProfileProjects(AgentProfile profile, List<UUID> projectIds) {
        agentProfileProjectRepository.deleteByIdAgentProfileId(profile.getId());
        if (projectIds == null || projectIds.isEmpty()) {
            return;
        }
        Set<UUID> distinctProjectIds = new LinkedHashSet<>(projectIds);
        for (UUID projectId : distinctProjectIds) {
            Project project = activeProject(required(projectId, "projectId"));
            if (!profile.getWorkspaceId().equals(project.getWorkspaceId())) {
                throw badRequest("Agent profile project scope must stay within the profile workspace");
            }
            AgentProfileProject scope = new AgentProfileProject();
            scope.setId(new AgentProfileProjectId(profile.getId(), project.getId()));
            agentProfileProjectRepository.save(scope);
        }
    }

    private void appendTaskEvent(AgentTask task, String eventType, String severity, String message, JsonNode metadata) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setAgentTaskId(task.getId());
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setMessage(message);
        event.setMetadata(metadata == null ? objectMapper.createObjectNode() : metadata);
        agentTaskEventRepository.save(event);
    }

    private void recordAgentTaskEvent(AgentTask task, WorkItem item, AgentProvider provider, AgentProfile profile, String eventType, UUID actorId, String message) {
        ObjectNode payload = workItemPayload(item, actorId)
                .put("agentTaskId", task.getId().toString())
                .put("agentProfileId", profile.getId().toString())
                .put("agentUserId", profile.getUserId().toString())
                .put("providerId", provider.getId().toString())
                .put("providerKey", provider.getProviderKey())
                .put("status", task.getStatus());
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        domainEventService.record(task.getWorkspaceId(), "agent_task", task.getId(), eventType, payload);
    }

    private void recordWorkerWebhookDispatchRequested(
            AgentTask task,
            WorkItem item,
            AgentProvider provider,
            AgentProfile profile,
            JsonNode adapterDispatchPayload,
            UUID actorId,
            boolean retry
    ) {
        ObjectNode payload = workItemPayload(item, actorId)
                .put("agentTaskId", task.getId().toString())
                .put("agentProfileId", profile.getId().toString())
                .put("agentUserId", profile.getUserId().toString())
                .put("providerId", provider.getId().toString())
                .put("providerKey", provider.getProviderKey())
                .put("dispatchMode", task.getDispatchMode())
                .put("callbackUrl", provider.getCallbackUrl())
                .put("retry", retry);
        payload.set("adapterDispatchPayload", adapterDispatchPayload == null ? objectMapper.createObjectNode() : adapterDispatchPayload);
        domainEventService.record(task.getWorkspaceId(), "agent_task", task.getId(), "agent.worker.dispatch_requested", payload);
        appendTaskEvent(task, "worker_webhook_queued", "info", "Worker webhook dispatch queued.", objectMapper.createObjectNode()
                .put("callbackUrl", provider.getCallbackUrl())
                .put("retry", retry));
    }

    private void recordWorkItemAgentEvent(WorkItem item, String eventType, UUID actorId, AgentTask task, AgentProfile profile) {
        ObjectNode payload = workItemPayload(item, actorId)
                .put("agentTaskId", task.getId().toString())
                .put("agentProfileId", profile.getId().toString())
                .put("agentUserId", profile.getUserId().toString());
        domainEventService.record(item.getWorkspaceId(), "work_item", item.getId(), eventType, payload);
    }

    private void recordEvent(WorkItem item, String eventType, UUID actorId) {
        domainEventService.record(item.getWorkspaceId(), "work_item", item.getId(), eventType, workItemPayload(item, actorId));
    }

    private void recordWorkspaceEvent(UUID workspaceId, String aggregateType, UUID aggregateId, String eventType, UUID actorId, ObjectNode payload) {
        payload.put("workspaceId", workspaceId.toString()).put("actorUserId", actorId.toString());
        domainEventService.record(workspaceId, aggregateType, aggregateId, eventType, payload);
    }

    private ObjectNode payloadForProvider(AgentProvider provider, UUID actorId) {
        return objectMapper.createObjectNode()
                .put("providerId", provider.getId().toString())
                .put("providerKey", provider.getProviderKey())
                .put("providerType", provider.getProviderType())
                .put("actorUserId", actorId.toString());
    }

    private ObjectNode payloadForProfile(AgentProfile profile, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("agentProfileId", profile.getId().toString())
                .put("agentUserId", profile.getUserId().toString())
                .put("providerId", profile.getProviderId().toString())
                .put("actorUserId", actorId.toString());
        payload.set("projectIds", objectMapper.valueToTree(agentProfileProjectRepository.findByIdAgentProfileIdOrderByCreatedAtAsc(profile.getId()).stream()
                .map(scope -> scope.getId().getProjectId())
                .toList()));
        return payload;
    }

    private ObjectNode workItemPayload(WorkItem item, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("projectId", item.getProjectId().toString());
        if (actorId != null) {
            payload.put("actorUserId", actorId.toString());
        }
        return payload;
    }

    private ObjectNode actorPayload(UUID actorId) {
        return objectMapper.createObjectNode().put("actorUserId", actorId.toString());
    }

    private JsonNode contextSnapshot(WorkItem item, AgentProfile profile, AgentProvider provider) {
        ObjectNode snapshot = objectMapper.createObjectNode()
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("workspaceId", item.getWorkspaceId().toString())
                .put("projectId", item.getProjectId().toString())
                .put("title", item.getTitle())
                .put("agentProfileId", profile.getId().toString())
                .put("agentUserId", profile.getUserId().toString())
                .put("providerId", provider.getId().toString())
                .put("providerType", provider.getProviderType());
        snapshot.set("agentProjectIds", objectMapper.valueToTree(agentProfileProjectRepository.findByIdAgentProfileIdOrderByCreatedAtAsc(profile.getId()).stream()
                .map(scope -> scope.getId().getProjectId())
                .toList()));
        if (item.getDescriptionMarkdown() != null) {
            snapshot.put("descriptionMarkdown", item.getDescriptionMarkdown());
        }
        if (item.getDescriptionDocument() != null) {
            snapshot.set("descriptionDocument", item.getDescriptionDocument());
        }
        return snapshot;
    }

    private void writeAssignmentHistory(UUID workItemId, UUID fromUserId, UUID toUserId, UUID actorId) {
        WorkItemAssignmentHistory history = new WorkItemAssignmentHistory();
        history.setWorkItemId(workItemId);
        history.setFromUserId(fromUserId);
        history.setToUserId(toUserId);
        history.setChangedById(actorId);
        workItemAssignmentHistoryRepository.save(history);
    }

    private void writeStatusHistory(UUID workItemId, UUID fromStatusId, UUID toStatusId, UUID actorId) {
        WorkItemStatusHistory history = new WorkItemStatusHistory();
        history.setWorkItemId(workItemId);
        history.setFromStatusId(fromStatusId);
        history.setToStatusId(toStatusId);
        history.setChangedById(actorId);
        workItemStatusHistoryRepository.save(history);
    }

    private void transitionAcceptedWorkToApproval(AgentTask task, WorkItem item, UUID actorId) {
        if (!permissionService.hasProjectPermission(actorId, item.getProjectId(), "work_item.transition")) {
            appendTaskEvent(task, "approval_transition_skipped", "warning", "Accepting user cannot transition the work item to Approval.", actorPayload(actorId));
            return;
        }
        WorkflowAssignment assignment = workflowAssignmentRepository.findByProjectIdAndWorkItemTypeId(item.getProjectId(), item.getTypeId())
                .orElse(null);
        if (assignment == null) {
            appendTaskEvent(task, "approval_transition_skipped", "warning", "No workflow assignment was available for approval transition.", actorPayload(actorId));
            return;
        }
        WorkflowStatus approval = workflowStatusRepository.findByWorkflowIdAndKeyIgnoreCase(assignment.getWorkflowId(), "approval")
                .orElse(null);
        if (approval == null || approval.getId().equals(item.getStatusId())) {
            return;
        }
        WorkflowTransition transition = workflowTransitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(
                        assignment.getWorkflowId(),
                        item.getStatusId(),
                        approval.getId()
                )
                .orElse(null);
        if (transition == null) {
            appendTaskEvent(task, "approval_transition_skipped", "info", "No direct transition to Approval was available from the current status.", actorPayload(actorId));
            return;
        }

        UUID fromStatusId = item.getStatusId();
        item.setStatusId(approval.getId());
        item.setUpdatedById(actorId);
        applyTransitionActions(item, transition.getId());
        WorkItem saved = workItemRepository.saveAndFlush(item);
        writeStatusHistory(saved.getId(), fromStatusId, saved.getStatusId(), actorId);
        appendTaskEvent(task, "approval_transitioned", "info", "Accepted agent result moved the work item to Approval.", actorPayload(actorId));
        recordEvent(saved, "work_item.status_changed", actorId);
        ObjectNode payload = workItemPayload(saved, actorId)
                .put("agentTaskId", task.getId().toString())
                .put("transitionKey", transition.getKey())
                .put("toStatusKey", approval.getKey());
        domainEventService.record(saved.getWorkspaceId(), "work_item", saved.getId(), "work_item.agent_acceptance_transitioned", payload);
    }

    private void applyTransitionActions(WorkItem item, UUID transitionId) {
        for (WorkflowTransitionAction action : workflowTransitionActionRepository.findByTransitionIdAndEnabledTrueOrderByPositionAsc(transitionId)) {
            if ("set_resolution".equals(action.getActionType()) && action.getConfig() != null && action.getConfig().hasNonNull("resolutionId")) {
                item.setResolutionId(UUID.fromString(action.getConfig().get("resolutionId").asText()));
            }
        }
    }

    private void assertAgentCapacity(AgentProfile profile) {
        long activeCount = agentTaskRepository.countByAgentProfileIdAndStatusIn(profile.getId(), ACTIVE_TASK_STATUSES);
        if (activeCount >= profile.getMaxConcurrentTasks()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent profile has reached its concurrency limit");
        }
    }

    private void requireAgentProjectPermission(AgentProfile profile, WorkItem item, String permissionKey, String message) {
        if (!permissionService.hasProjectPermission(profile.getUserId(), item.getProjectId(), permissionKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireAgentAvailableForProject(AgentProfile profile, WorkItem item) {
        if (agentProfileProjectRepository.existsByIdAgentProfileId(profile.getId())
                && !agentProfileProjectRepository.existsByIdAgentProfileIdAndIdProjectId(profile.getId(), item.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent profile is not available for this project");
        }
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> notFound("Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        return project;
    }

    private WorkItem activeWorkItem(UUID workItemId) {
        return workItemRepository.findByIdAndDeletedAtIsNull(workItemId).orElseThrow(() -> notFound("Work item not found"));
    }

    private AgentProvider activeProvider(UUID workspaceId, UUID providerId) {
        AgentProvider provider = agentProviderRepository.findByIdAndWorkspaceId(providerId, workspaceId).orElseThrow(() -> notFound("Agent provider not found"));
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw badRequest("Agent provider is disabled");
        }
        return provider;
    }

    private AuthenticatedWorker authenticatedWorker(UUID workspaceId, String providerKey, String workerToken, String providedWorkerId) {
        AgentProvider provider = agentProviderRepository.findByWorkspaceIdAndProviderKey(workspaceId, normalizeKey(providerKey))
                .orElseThrow(() -> notFound("Agent provider not found"));
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw badRequest("Agent provider is disabled");
        }
        if (!"generic_worker".equals(provider.getProviderType())) {
            throw badRequest("Worker protocol is only available for generic worker providers");
        }
        if (!hasText(workerToken)) {
            throw unauthorized("Missing worker token");
        }
        for (AgentProviderCredential credential : agentProviderCredentialRepository.findByProviderIdAndCredentialTypeAndActiveTrue(provider.getId(), "worker_token")) {
            if (credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(OffsetDateTime.now())) {
                continue;
            }
            if (!workerToken.equals(secretCipherService.decrypt(credential.getEncryptedSecret()))) {
                continue;
            }
            String credentialWorkerId = workerIdFromMetadata(credential.getMetadata());
            if (!hasText(credentialWorkerId)) {
                throw unauthorized("Invalid worker token");
            }
            if (hasText(providedWorkerId) && !credentialWorkerId.equals(providedWorkerId.trim())) {
                throw unauthorized("Worker token does not match the requested worker");
            }
            return new AuthenticatedWorker(provider, credentialWorkerId);
        }
        throw unauthorized("Invalid worker token");
    }

    private record AuthenticatedWorker(AgentProvider provider, String workerId) {
    }

    private JsonNode credentialMetadata(String credentialType, Object metadata) {
        JsonNode normalized = toJson(metadata);
        if (!"worker_token".equals(credentialType)) {
            return normalized;
        }
        String workerId = workerIdFromMetadata(normalized);
        if (!hasText(workerId)) {
            throw badRequest("worker_token metadata.workerId is required");
        }
        return normalized;
    }

    private String workerIdFromMetadata(JsonNode metadata) {
        if (metadata == null || !metadata.isObject() || !hasText(metadata.path("workerId").asText(null))) {
            return null;
        }
        return metadata.path("workerId").asText().trim();
    }

    private AgentTask workerTask(AgentProvider provider, UUID taskId) {
        AgentTask task = agentTaskRepository.findByIdAndWorkspaceId(taskId, provider.getWorkspaceId())
                .orElseThrow(() -> notFound("Agent task not found"));
        if (!provider.getId().equals(task.getProviderId())) {
            throw notFound("Agent task not found");
        }
        return task;
    }

    private AgentProfile activeProfile(UUID workspaceId, UUID profileId) {
        AgentProfile profile = agentProfileRepository.findByIdAndWorkspaceId(profileId, workspaceId).orElseThrow(() -> notFound("Agent profile not found"));
        if (!"active".equals(profile.getStatus())) {
            throw badRequest("Agent profile is not active");
        }
        return profile;
    }

    private Role resolveWorkspaceRole(UUID workspaceId, UUID roleId) {
        if (roleId != null) {
            return roleRepository.findByIdAndWorkspaceIdAndProjectIdIsNull(roleId, workspaceId)
                    .orElseThrow(() -> badRequest("Workspace role not found"));
        }
        return roleRepository.findByWorkspaceIdAndKeyIgnoreCaseAndProjectIdIsNull(workspaceId, "member")
                .orElseThrow(() -> badRequest("Default member role not found"));
    }

    private User createAgentUser(String displayName, String requestedUsername) {
        String username = uniqueUsername(firstText(requestedUsername, displayName));
        User user = new User();
        user.setEmail(username + "-" + UUID.randomUUID() + "@agent.trasck.local");
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setAccountType("agent");
        user.setEmailVerified(true);
        user.setActive(true);
        return userRepository.save(user);
    }

    private void createWorkspaceMembership(UUID workspaceId, UUID userId, UUID roleId) {
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(userId);
        membership.setRoleId(roleId);
        membership.setStatus("active");
        membership.setJoinedAt(OffsetDateTime.now());
        workspaceMembershipRepository.save(membership);
    }

    private AgentProviderAdapter adapter(String providerType) {
        return adapters.stream()
                .filter(candidate -> candidate.providerType().equals(providerType))
                .findFirst()
                .orElseThrow(() -> badRequest("No agent adapter is registered for provider type " + providerType));
    }

    private JsonNode repositoryConfig(RepositoryConnectionRequest request) {
        JsonNode config = toJson(request.config());
        ObjectNode object = config.isObject() ? (ObjectNode) config.deepCopy() : objectMapper.createObjectNode().set("value", config);
        object.set("providerMetadata", toJson(request.providerMetadata()));
        return object;
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private String normalizeProviderType(String providerType) {
        String normalized = normalizeKey(firstText(providerType, "simulated"));
        if (!List.of("simulated", "codex", "claude_code", "generic_worker").contains(normalized)) {
            throw badRequest("Agent provider type must be simulated, codex, claude_code, or generic_worker");
        }
        return normalized;
    }

    private String normalizeDispatchMode(String dispatchMode) {
        String normalized = normalizeKey(firstText(dispatchMode, "managed"));
        if (!List.of("webhook_push", "polling", "managed", "manual").contains(normalized)) {
            throw badRequest("Unsupported dispatch mode");
        }
        return normalized;
    }

    private String normalizeProfileStatus(String status) {
        String normalized = normalizeKey(firstText(status, "active"));
        if (!List.of("active", "paused", "disabled").contains(normalized)) {
            throw badRequest("Unsupported agent profile status");
        }
        return normalized;
    }

    private String normalizeRepositoryProvider(String provider) {
        String normalized = normalizeKey(firstText(provider, "generic_git"));
        if (!List.of("generic_git", "github", "gitlab").contains(normalized)) {
            throw badRequest("Repository provider must be generic_git, github, or gitlab");
        }
        return normalized;
    }

    private String normalizeCallbackStatus(String status) {
        String normalized = normalizeKey(requiredText(status, "status"));
        if (!List.of("running", "waiting_for_input", "completed", "failed").contains(normalized)) {
            throw badRequest("Unsupported agent callback status");
        }
        return normalized;
    }

    private int normalizeMaxConcurrentTasks(Integer maxConcurrentTasks) {
        int normalized = maxConcurrentTasks == null ? 1 : maxConcurrentTasks;
        if (normalized < 1) {
            throw badRequest("maxConcurrentTasks must be greater than 0");
        }
        return normalized;
    }

    private String normalizeSenderType(String senderType) {
        String normalized = normalizeKey(firstText(senderType, "agent"));
        if (!List.of("human", "agent", "system").contains(normalized)) {
            return "agent";
        }
        return normalized;
    }

    private String normalizeSeverity(String severity) {
        String normalized = normalizeKey(firstText(severity, "info"));
        if (!List.of("debug", "info", "warning", "error").contains(normalized)) {
            return "info";
        }
        return normalized;
    }

    private AgentDispatchAttemptFilter dispatchAttemptFilter(
            UUID agentTaskId,
            UUID providerId,
            UUID agentProfileId,
            UUID workItemId,
            String attemptType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo
    ) {
        if (startedFrom != null && startedTo != null && startedFrom.isAfter(startedTo)) {
            throw badRequest("startedFrom must be before or equal to startedTo");
        }
        return new AgentDispatchAttemptFilter(
                agentTaskId,
                providerId,
                agentProfileId,
                workItemId,
                normalizeDispatchAttemptType(attemptType),
                normalizeDispatchAttemptStatus(status),
                startedFrom,
                startedTo
        );
    }

    private String normalizeDispatchAttemptType(String attemptType) {
        if (!hasText(attemptType) || "all".equalsIgnoreCase(attemptType.trim())) {
            return null;
        }
        String normalized = normalizeKey(attemptType);
        if (!List.of("dispatch", "retry", "cancel").contains(normalized)) {
            throw badRequest("attemptType must be dispatch, retry, or cancel");
        }
        return normalized;
    }

    private String normalizeDispatchAttemptStatus(String status) {
        if (!hasText(status) || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String normalized = normalizeKey(status);
        if (!List.of("succeeded", "failed").contains(normalized)) {
            throw badRequest("status must be succeeded or failed");
        }
        return normalized;
    }

    private String normalizeRuntimePreviewAction(String action) {
        String normalized = normalizeKey(firstText(action, "dispatched"));
        if (!List.of("dispatched", "retried", "cancelled", "canceled").contains(normalized)) {
            throw badRequest("action must be dispatched, retried, or cancelled");
        }
        return "canceled".equals(normalized) ? "cancelled" : normalized;
    }

    private int normalizeListLimit(Integer limit, int defaultLimit, int maxLimit) {
        int normalized = limit == null ? defaultLimit : limit;
        if (normalized < 1 || normalized > maxLimit) {
            throw badRequest("limit must be between 1 and " + maxLimit);
        }
        return normalized;
    }

    private ObjectNode dispatchAttemptFilterPayload(AgentDispatchAttemptFilter filter) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (filter.agentTaskId() != null) {
            payload.put("agentTaskId", filter.agentTaskId().toString());
        }
        if (filter.providerId() != null) {
            payload.put("providerId", filter.providerId().toString());
        }
        if (filter.agentProfileId() != null) {
            payload.put("agentProfileId", filter.agentProfileId().toString());
        }
        if (filter.workItemId() != null) {
            payload.put("workItemId", filter.workItemId().toString());
        }
        if (filter.attemptType() != null) {
            payload.put("attemptType", filter.attemptType());
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
        return payload;
    }

    private boolean isTerminal(String status) {
        return List.of("completed", "failed", "canceled").contains(status);
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
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

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
        }
        return value;
    }

    private String firstText(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : fallback;
    }

    private String textAt(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private ObjectNode workerMetadata(String workerId, Object metadata) {
        ObjectNode result = objectMapper.createObjectNode();
        if (hasText(workerId)) {
            result.put("workerId", workerId.trim());
        }
        if (metadata != null) {
            result.set("metadata", toJson(metadata));
        }
        return result;
    }

    private String uniqueUsername(String base) {
        String normalizedBase = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (normalizedBase.isBlank()) {
            normalizedBase = "agent";
        }
        String candidate = normalizedBase;
        int suffix = 1;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = normalizedBase + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record AgentDispatchAttemptFilter(
            UUID agentTaskId,
            UUID providerId,
            UUID agentProfileId,
            UUID workItemId,
            String attemptType,
            String status,
            OffsetDateTime startedFrom,
            OffsetDateTime startedTo
    ) {
    }
}
