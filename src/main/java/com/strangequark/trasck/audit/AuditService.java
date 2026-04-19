package com.strangequark.trasck.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.activity.Attachment;
import com.strangequark.trasck.activity.AttachmentRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.activity.storage.AttachmentStorageService;
import com.strangequark.trasck.activity.storage.AttachmentUpload;
import com.strangequark.trasck.activity.storage.StoredAttachment;
import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.api.PageCursorCodec;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.integration.ExportFileResponse;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.integration.ExportJob;
import com.strangequark.trasck.integration.ExportJobRepository;
import com.strangequark.trasck.integration.ExportJobResponse;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditService {

    private static final int MAX_RETENTION_PRUNE_EXPORT_ROWS = 10_000;
    private static final DateTimeFormatter EXPORT_FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final OffsetDateTime EXPORT_CURSOR_FLOOR = OffsetDateTime.parse("0001-01-01T00:00:00Z");

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final AuditRetentionPolicyRepository auditRetentionPolicyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final ExportJobRepository exportJobRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogEntryRepository auditLogEntryRepository,
            AuditRetentionPolicyRepository auditRetentionPolicyRepository,
            WorkspaceRepository workspaceRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            AttachmentRepository attachmentRepository,
            AttachmentStorageService attachmentStorageService,
            ExportJobRepository exportJobRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService,
            ObjectMapper objectMapper
    ) {
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.auditRetentionPolicyRepository = auditRetentionPolicyRepository;
        this.workspaceRepository = workspaceRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.exportJobRepository = exportJobRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<AuditLogEntryResponse> listAuditLog(UUID workspaceId, Integer limit, String cursor) {
        activeWorkspace(workspaceId);
        int pageLimit = normalizeLimit(limit);
        PageCursorCodec.TimestampCursor decoded = cursor == null || cursor.isBlank() ? null : PageCursorCodec.decodeTimestamp(cursor);
        List<AuditLogEntry> page = decoded == null
                ? auditLogEntryRepository.findFirstCursorPage(workspaceId, pageLimit + 1)
                : auditLogEntryRepository.findCursorPageAfter(
                        workspaceId,
                        decoded.createdAt(),
                        decoded.id(),
                        pageLimit + 1
                );
        boolean hasMore = page.size() > pageLimit;
        List<AuditLogEntry> items = hasMore ? page.subList(0, pageLimit) : page;
        String nextCursor = hasMore
                ? PageCursorCodec.encodeTimestamp(items.get(items.size() - 1).getCreatedAt(), items.get(items.size() - 1).getId().toString())
                : null;
        return new CursorPageResponse<>(
                items.stream()
                .map(AuditLogEntryResponse::from)
                        .toList(),
                nextCursor,
                hasMore,
                pageLimit
        );
    }

    @Transactional(readOnly = true)
    public AuditRetentionPolicyResponse getRetentionPolicy(UUID workspaceId) {
        activeWorkspace(workspaceId);
        return auditRetentionPolicyRepository.findByWorkspaceId(workspaceId)
                .map(AuditRetentionPolicyResponse::from)
                .orElseGet(() -> AuditRetentionPolicyResponse.permanent(workspaceId));
    }

    @Transactional
    public AuditRetentionPolicyResponse updateRetentionPolicy(UUID workspaceId, AuditRetentionPolicyRequest request) {
        activeWorkspace(workspaceId);
        AuditRetentionPolicyRequest update = required(request, "request");
        boolean enabled = Boolean.TRUE.equals(update.retentionEnabled());
        if (enabled && (update.retentionDays() == null || update.retentionDays() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "retentionDays must be greater than 0 when retention is enabled");
        }
        UUID actorId = currentUserService.requireUserId();
        AuditRetentionPolicy policy = auditRetentionPolicyRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    AuditRetentionPolicy created = new AuditRetentionPolicy();
                    created.setWorkspaceId(workspaceId);
                    return created;
                });
        ObjectNode before = policySnapshot(policy);
        policy.setRetentionEnabled(enabled);
        policy.setRetentionDays(enabled ? update.retentionDays() : null);
        policy.setUpdatedById(actorId);
        AuditRetentionPolicy saved = auditRetentionPolicyRepository.save(policy);
        ObjectNode after = policySnapshot(saved);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("actorUserId", actorId.toString());
        payload.set("before", before);
        payload.set("after", after);
        domainEventService.record(workspaceId, "audit_retention_policy", saved.getId(), "audit.retention_policy_updated", payload);
        return AuditRetentionPolicyResponse.from(saved);
    }

    @Transactional
    public AuditRetentionExportResponse exportRetentionCandidates(UUID workspaceId, Integer limit) {
        activeWorkspace(workspaceId);
        UUID actorId = currentUserService.requireUserId();
        RetentionExportSnapshot snapshot = retentionExportSnapshot(workspaceId, normalizeExportLimit(limit));
        StoredRetentionExport export = storeRetentionExport(workspaceId, actorId, snapshot);
        return exportResponse(snapshot, export);
    }

    @Transactional
    public AuditRetentionPruneResponse pruneRetentionCandidates(UUID workspaceId) {
        activeWorkspace(workspaceId);
        UUID actorId = currentUserService.requireUserId();
        AuditRetentionPruneResponse response = pruneWorkspace(workspaceId, actorId);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("actorUserId", actorId.toString())
                .put("entriesEligible", response.entriesEligible())
                .put("entriesPruned", response.entriesPruned());
        if (response.cutoff() != null) {
            payload.put("cutoff", response.cutoff().toString());
        }
        if (response.exportJobId() != null) {
            payload.put("exportJobId", response.exportJobId().toString());
        }
        if (response.fileAttachmentId() != null) {
            payload.put("fileAttachmentId", response.fileAttachmentId().toString());
        }
        domainEventService.record(workspaceId, "audit_retention_policy", workspaceId, "audit.retention_pruned", payload);
        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ExportJobResponse> listExportJobs(UUID workspaceId, String exportType, Integer limit, String cursor) {
        activeWorkspace(workspaceId);
        requireWorkspaceAdmin(workspaceId);
        int pageLimit = normalizeLimit(limit);
        PageCursorCodec.TimestampCursor decoded = cursor == null || cursor.isBlank() ? null : PageCursorCodec.decodeTimestamp(cursor);
        String normalizedExportType = hasText(exportType) ? exportType.trim().toLowerCase() : null;
        List<ExportJob> page = exportJobPage(workspaceId, normalizedExportType, decoded, pageLimit + 1);
        boolean hasMore = page.size() > pageLimit;
        List<ExportJob> items = hasMore ? page.subList(0, pageLimit) : page;
        String nextCursor = hasMore
                ? PageCursorCodec.encodeTimestamp(exportJobCursorStartedAt(items.get(items.size() - 1)), items.get(items.size() - 1).getId().toString())
                : null;
        return new CursorPageResponse<>(
                items.stream()
                        .map(job -> ExportJobResponse.from(job, exportAttachment(job, false)))
                        .toList(),
                nextCursor,
                hasMore,
                pageLimit
        );
    }

    private List<ExportJob> exportJobPage(
            UUID workspaceId,
            String exportType,
            PageCursorCodec.TimestampCursor cursor,
            int limit
    ) {
        if (cursor == null && exportType == null) {
            return exportJobRepository.findFirstCursorPage(workspaceId, limit);
        }
        if (cursor == null) {
            return exportJobRepository.findFirstCursorPageByExportType(workspaceId, exportType, limit);
        }
        if (exportType == null) {
            return exportJobRepository.findCursorPageAfter(workspaceId, cursor.createdAt(), cursor.id(), limit);
        }
        return exportJobRepository.findCursorPageAfterByExportType(
                workspaceId,
                exportType,
                cursor.createdAt(),
                cursor.id(),
                limit
        );
    }

    private OffsetDateTime exportJobCursorStartedAt(ExportJob job) {
        return job.getStartedAt() == null ? EXPORT_CURSOR_FLOOR : job.getStartedAt();
    }

    @Transactional(readOnly = true)
    public ExportJobResponse getExportJob(UUID workspaceId, UUID exportJobId) {
        activeWorkspace(workspaceId);
        requireWorkspaceAdmin(workspaceId);
        ExportJob job = exportJob(workspaceId, exportJobId);
        return ExportJobResponse.from(job, exportAttachment(job, false));
    }

    @Transactional(readOnly = true)
    public ExportFileResponse downloadExportJob(UUID workspaceId, UUID exportJobId) {
        activeWorkspace(workspaceId);
        requireWorkspaceAdmin(workspaceId);
        ExportJob job = exportJob(workspaceId, exportJobId);
        Attachment attachment = exportAttachment(job, true);
        AttachmentStorageConfig storageConfig = attachmentStorageConfigRepository.findByIdAndWorkspaceId(
                        attachment.getStorageConfigId(),
                        workspaceId
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export file storage config not found"));
        byte[] bytes = attachmentStorageService.read(storageConfig, attachment.getStorageKey());
        return new ExportFileResponse(attachment.getFilename(), attachment.getContentType(), attachment.getChecksum(), bytes);
    }

    @Scheduled(cron = "${trasck.audit.retention.cron:0 45 3 * * *}", zone = "UTC")
    @Transactional
    public void runScheduledRetentionPruning() {
        for (AuditRetentionPolicy policy : auditRetentionPolicyRepository.findByRetentionEnabledTrue()) {
            if (policy.getWorkspaceId() != null) {
                pruneWorkspace(policy.getWorkspaceId(), null);
            }
        }
    }

    private AuditRetentionPruneResponse pruneWorkspace(UUID workspaceId, UUID actorId) {
        RetentionExportSnapshot snapshot = retentionExportSnapshot(workspaceId, MAX_RETENTION_PRUNE_EXPORT_ROWS);
        if (snapshot.entriesEligible() > MAX_RETENTION_PRUNE_EXPORT_ROWS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Too many audit log entries are eligible for a single retention prune export"
            );
        }
        StoredRetentionExport export = snapshot.entriesEligible() == 0
                ? null
                : storeRetentionExport(workspaceId, actorId, snapshot);
        int pruned = snapshot.cutoff() == null ? 0 : auditLogEntryRepository.deleteRetainedEntries(workspaceId, snapshot.cutoff());
        return new AuditRetentionPruneResponse(
                workspaceId,
                Boolean.TRUE.equals(snapshot.policy().getRetentionEnabled()),
                snapshot.policy().getRetentionDays(),
                snapshot.cutoff(),
                snapshot.entriesEligible(),
                pruned,
                export == null ? null : export.exportJobId(),
                export == null ? null : export.fileAttachmentId()
        );
    }

    private RetentionExportSnapshot retentionExportSnapshot(UUID workspaceId, int limit) {
        AuditRetentionPolicy policy = retentionPolicy(workspaceId);
        OffsetDateTime cutoff = retentionCutoff(policy);
        long eligible = cutoff == null ? 0 : auditLogEntryRepository.countByWorkspaceIdAndCreatedAtBefore(workspaceId, cutoff);
        List<AuditLogEntryResponse> entries = cutoff == null
                ? List.of()
                : auditLogEntryRepository.findByWorkspaceIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                        workspaceId,
                        cutoff,
                        PageRequest.of(0, limit)
                ).stream().map(AuditLogEntryResponse::from).toList();
        return new RetentionExportSnapshot(policy, cutoff, eligible, entries);
    }

    private StoredRetentionExport storeRetentionExport(UUID workspaceId, UUID actorId, RetentionExportSnapshot snapshot) {
        AttachmentStorageConfig storageConfig = attachmentStorageConfigRepository.findFirstByWorkspaceIdAndActiveTrueAndDefaultConfigTrue(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default attachment storage config not found"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String filename = "audit-retention-"
                + workspaceId
                + "-"
                + now.format(EXPORT_FILENAME_TIME)
                + ".json";
        byte[] content = retentionExportContent(workspaceId, actorId, snapshot, now);
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
            exportJob.setExportType("audit_retention");
            exportJob.setStatus("completed");
            exportJob.setFileAttachmentId(savedAttachment.getId());
            exportJob.setStartedAt(now);
            exportJob.setFinishedAt(now);
            ExportJob savedExportJob = exportJobRepository.save(exportJob);
            return new StoredRetentionExport(
                    savedExportJob.getId(),
                    savedAttachment.getId(),
                    filename,
                    stored.storageKey(),
                    stored.checksum(),
                    stored.sizeBytes()
            );
        } catch (RuntimeException ex) {
            attachmentStorageService.delete(storageConfig, stored.storageKey());
            throw ex;
        }
    }

    private byte[] retentionExportContent(UUID workspaceId, UUID actorId, RetentionExportSnapshot snapshot, OffsetDateTime exportedAt) {
        ObjectNode document = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("exportedAt", exportedAt.toString())
                .put("retentionEnabled", Boolean.TRUE.equals(snapshot.policy().getRetentionEnabled()))
                .put("entriesEligible", snapshot.entriesEligible())
                .put("entriesIncluded", snapshot.entries().size());
        if (actorId != null) {
            document.put("actorUserId", actorId.toString());
        }
        if (snapshot.policy().getRetentionDays() != null) {
            document.put("retentionDays", snapshot.policy().getRetentionDays());
        }
        if (snapshot.cutoff() != null) {
            document.put("cutoff", snapshot.cutoff().toString());
        }
        document.set("entries", objectMapper.valueToTree(snapshot.entries()));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize audit retention export", ex);
        }
    }

    private AuditRetentionExportResponse exportResponse(RetentionExportSnapshot snapshot, StoredRetentionExport export) {
        return new AuditRetentionExportResponse(
                snapshot.policy().getWorkspaceId(),
                Boolean.TRUE.equals(snapshot.policy().getRetentionEnabled()),
                snapshot.policy().getRetentionDays(),
                snapshot.cutoff(),
                snapshot.entriesEligible(),
                export.exportJobId(),
                export.fileAttachmentId(),
                export.filename(),
                export.storageKey(),
                export.checksum(),
                export.sizeBytes(),
                snapshot.entries()
        );
    }

    private AuditRetentionPolicy retentionPolicy(UUID workspaceId) {
        return auditRetentionPolicyRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    AuditRetentionPolicy policy = new AuditRetentionPolicy();
                    policy.setWorkspaceId(workspaceId);
                    policy.setRetentionEnabled(false);
                    return policy;
                });
    }

    private OffsetDateTime retentionCutoff(AuditRetentionPolicy policy) {
        if (!Boolean.TRUE.equals(policy.getRetentionEnabled()) || policy.getRetentionDays() == null) {
            return null;
        }
        return OffsetDateTime.now().minusDays(policy.getRetentionDays());
    }

    private ObjectNode policySnapshot(AuditRetentionPolicy policy) {
        ObjectNode snapshot = objectMapper.createObjectNode()
                .put("retentionEnabled", Boolean.TRUE.equals(policy.getRetentionEnabled()));
        if (policy.getId() != null) {
            snapshot.put("id", policy.getId().toString());
        }
        if (policy.getWorkspaceId() != null) {
            snapshot.put("workspaceId", policy.getWorkspaceId().toString());
        }
        if (policy.getRetentionDays() != null) {
            snapshot.put("retentionDays", policy.getRetentionDays());
        }
        return snapshot;
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return workspace;
    }

    private ExportJob exportJob(UUID workspaceId, UUID exportJobId) {
        ExportJob job = exportJobRepository.findById(exportJobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found"));
        if (!workspaceId.equals(job.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found");
        }
        return job;
    }

    private Attachment exportAttachment(ExportJob job, boolean required) {
        if (job.getFileAttachmentId() == null) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export file not found");
            }
            return null;
        }
        Attachment attachment = attachmentRepository.findById(job.getFileAttachmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export file not found"));
        if (!job.getWorkspaceId().equals(attachment.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export file not found");
        }
        return attachment;
    }

    private void requireWorkspaceAdmin(UUID workspaceId) {
        permissionService.requireWorkspacePermission(currentUserService.requireUserId(), workspaceId, "workspace.admin");
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private int normalizeExportLimit(Integer limit) {
        if (limit == null) {
            return 500;
        }
        return Math.max(1, Math.min(limit, 1000));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private record RetentionExportSnapshot(
            AuditRetentionPolicy policy,
            OffsetDateTime cutoff,
            long entriesEligible,
            List<AuditLogEntryResponse> entries
    ) {
    }

    private record StoredRetentionExport(
            UUID exportJobId,
            UUID fileAttachmentId,
            String filename,
            String storageKey,
            String checksum,
            long sizeBytes
    ) {
    }
}
