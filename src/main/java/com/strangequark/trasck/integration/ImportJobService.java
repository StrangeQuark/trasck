package com.strangequark.trasck.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportJobService {

    private final ObjectMapper objectMapper;
    private final ImportJobRepository importJobRepository;
    private final ImportJobRecordRepository importJobRecordRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public ImportJobService(
            ObjectMapper objectMapper,
            ImportJobRepository importJobRepository,
            ImportJobRecordRepository importJobRecordRepository,
            WorkspaceRepository workspaceRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.importJobRepository = importJobRepository;
        this.importJobRecordRepository = importJobRecordRepository;
        this.workspaceRepository = workspaceRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<ImportJobResponse> listImportJobs(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        return importJobRepository.findByWorkspaceIdOrderByStartedAtDesc(workspaceId).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getImportJob(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return response(job);
    }

    @Transactional
    public ImportJobResponse createImportJob(UUID workspaceId, ImportJobRequest request) {
        ImportJobRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        ImportJob job = new ImportJob();
        job.setWorkspaceId(workspaceId);
        job.setRequestedById(actorId);
        job.setProvider(requiredText(createRequest.provider(), "provider").toLowerCase());
        job.setStatus("queued");
        job.setConfig(toJsonObject(createRequest.config()));
        ImportJob saved = importJobRepository.save(job);
        recordJobEvent(saved, "import_job.created", actorId);
        return response(saved);
    }

    @Transactional
    public ImportJobResponse startImportJob(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        job.setStatus("running");
        job.setStartedAt(OffsetDateTime.now());
        ImportJob saved = importJobRepository.save(job);
        recordJobEvent(saved, "import_job.started", actorId);
        return response(saved);
    }

    @Transactional
    public ImportJobResponse completeImportJob(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        job.setStatus("completed");
        job.setFinishedAt(OffsetDateTime.now());
        ImportJob saved = importJobRepository.save(job);
        recordJobEvent(saved, "import_job.completed", actorId);
        return response(saved);
    }

    @Transactional
    public ImportJobResponse failImportJob(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        job.setStatus("failed");
        job.setFinishedAt(OffsetDateTime.now());
        ImportJob saved = importJobRepository.save(job);
        recordJobEvent(saved, "import_job.failed", actorId);
        return response(saved);
    }

    @Transactional
    public ImportJobResponse cancelImportJob(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        job.setStatus("cancelled");
        job.setFinishedAt(OffsetDateTime.now());
        ImportJob saved = importJobRepository.save(job);
        recordJobEvent(saved, "import_job.cancelled", actorId);
        return response(saved);
    }

    @Transactional
    public ImportJobRecordResponse createRecord(UUID importJobId, ImportJobRecordRequest request) {
        ImportJobRecordRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        ImportJobRecord record = new ImportJobRecord();
        record.setImportJobId(job.getId());
        applyRecordRequest(record, createRequest, true);
        ImportJobRecord saved = importJobRecordRepository.save(record);
        recordJobEvent(job, "import_job.record_created", actorId);
        return ImportJobRecordResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ImportJobRecordResponse> listRecords(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return importJobRecordRepository.findByImportJobIdOrderBySourceTypeAscSourceIdAsc(job.getId()).stream()
                .map(ImportJobRecordResponse::from)
                .toList();
    }

    private void applyRecordRequest(ImportJobRecord record, ImportJobRecordRequest request, boolean create) {
        if (create || hasText(request.sourceType())) {
            record.setSourceType(requiredText(request.sourceType(), "sourceType"));
        }
        if (create || hasText(request.sourceId())) {
            record.setSourceId(requiredText(request.sourceId(), "sourceId"));
        }
        if (request.targetType() != null) {
            record.setTargetType(request.targetType());
        }
        if (request.targetId() != null) {
            record.setTargetId(request.targetId());
        }
        if (create || hasText(request.status())) {
            record.setStatus(hasText(request.status()) ? request.status().trim().toLowerCase() : "pending");
        }
        if (request.errorMessage() != null) {
            record.setErrorMessage(request.errorMessage());
        }
        if (create || request.rawPayload() != null) {
            record.setRawPayload(toJsonObject(request.rawPayload()));
        }
    }

    private ImportJobResponse response(ImportJob job) {
        return ImportJobResponse.from(job, importJobRecordRepository.findByImportJobIdOrderBySourceTypeAscSourceIdAsc(job.getId()));
    }

    private ImportJob importJob(UUID importJobId) {
        return importJobRepository.findById(importJobId).orElseThrow(() -> notFound("Import job not found"));
    }

    private ImportJob mutableImportJob(UUID importJobId) {
        ImportJob job = importJob(importJobId);
        if (List.of("completed", "failed", "cancelled").contains(job.getStatus())) {
            throw badRequest("Completed, failed, or cancelled import jobs cannot be changed");
        }
        return job;
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
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

    private void recordJobEvent(ImportJob job, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("importJobId", job.getId().toString())
                .put("provider", job.getProvider())
                .put("status", job.getStatus())
                .put("actorUserId", actorId.toString());
        domainEventService.record(job.getWorkspaceId(), "import_job", job.getId(), eventType, payload);
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
