package com.strangequark.trasck.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
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

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final AuditRetentionPolicyRepository auditRetentionPolicyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final CurrentUserService currentUserService;
    private final DomainEventService domainEventService;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogEntryRepository auditLogEntryRepository,
            AuditRetentionPolicyRepository auditRetentionPolicyRepository,
            WorkspaceRepository workspaceRepository,
            CurrentUserService currentUserService,
            DomainEventService domainEventService,
            ObjectMapper objectMapper
    ) {
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.auditRetentionPolicyRepository = auditRetentionPolicyRepository;
        this.workspaceRepository = workspaceRepository;
        this.currentUserService = currentUserService;
        this.domainEventService = domainEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntryResponse> listAuditLog(UUID workspaceId, Integer limit) {
        activeWorkspace(workspaceId);
        return auditLogEntryRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, normalizeLimit(limit))).stream()
                .map(AuditLogEntryResponse::from)
                .toList();
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

    @Transactional(readOnly = true)
    public AuditRetentionExportResponse exportRetentionCandidates(UUID workspaceId, Integer limit) {
        activeWorkspace(workspaceId);
        AuditRetentionPolicy policy = retentionPolicy(workspaceId);
        OffsetDateTime cutoff = retentionCutoff(policy);
        long eligible = cutoff == null ? 0 : auditLogEntryRepository.countByWorkspaceIdAndCreatedAtBefore(workspaceId, cutoff);
        List<AuditLogEntryResponse> entries = cutoff == null
                ? List.of()
                : auditLogEntryRepository.findByWorkspaceIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                        workspaceId,
                        cutoff,
                        PageRequest.of(0, normalizeExportLimit(limit))
                ).stream().map(AuditLogEntryResponse::from).toList();
        return new AuditRetentionExportResponse(
                workspaceId,
                Boolean.TRUE.equals(policy.getRetentionEnabled()),
                policy.getRetentionDays(),
                cutoff,
                eligible,
                entries
        );
    }

    @Transactional
    public AuditRetentionPruneResponse pruneRetentionCandidates(UUID workspaceId) {
        activeWorkspace(workspaceId);
        UUID actorId = currentUserService.requireUserId();
        AuditRetentionPruneResponse response = pruneWorkspace(workspaceId);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", workspaceId.toString())
                .put("actorUserId", actorId.toString())
                .put("entriesEligible", response.entriesEligible())
                .put("entriesPruned", response.entriesPruned());
        if (response.cutoff() != null) {
            payload.put("cutoff", response.cutoff().toString());
        }
        domainEventService.record(workspaceId, "audit_retention_policy", workspaceId, "audit.retention_pruned", payload);
        return response;
    }

    @Scheduled(cron = "${trasck.audit.retention.cron:0 45 3 * * *}", zone = "UTC")
    @Transactional
    public void runScheduledRetentionPruning() {
        for (AuditRetentionPolicy policy : auditRetentionPolicyRepository.findByRetentionEnabledTrue()) {
            if (policy.getWorkspaceId() != null) {
                pruneWorkspace(policy.getWorkspaceId());
            }
        }
    }

    private AuditRetentionPruneResponse pruneWorkspace(UUID workspaceId) {
        AuditRetentionPolicy policy = retentionPolicy(workspaceId);
        OffsetDateTime cutoff = retentionCutoff(policy);
        long eligible = cutoff == null ? 0 : auditLogEntryRepository.countByWorkspaceIdAndCreatedAtBefore(workspaceId, cutoff);
        int pruned = cutoff == null ? 0 : auditLogEntryRepository.deleteRetainedEntries(workspaceId, cutoff);
        return new AuditRetentionPruneResponse(
                workspaceId,
                Boolean.TRUE.equals(policy.getRetentionEnabled()),
                policy.getRetentionDays(),
                cutoff,
                eligible,
                pruned
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

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }
}
