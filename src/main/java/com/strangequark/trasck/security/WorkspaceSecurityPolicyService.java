package com.strangequark.trasck.security;

import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceSecurityPolicyService {

    private final WorkspaceSecurityPolicyRepository policyRepository;
    private final ProjectSecurityPolicyRepository projectPolicyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final ContentLimits defaults;

    public WorkspaceSecurityPolicyService(
            WorkspaceSecurityPolicyRepository policyRepository,
            ProjectSecurityPolicyRepository projectPolicyRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            @Value("${trasck.attachments.max-upload-bytes:10485760}") long maxAttachmentUploadBytes,
            @Value("${trasck.attachments.max-download-bytes:52428800}") long maxAttachmentDownloadBytes,
            @Value("${trasck.attachments.allowed-content-types:text/plain,text/markdown,text/csv,application/json,application/pdf,image/png,image/jpeg,image/gif,image/webp,application/zip,application/octet-stream}") String attachmentContentTypes,
            @Value("${trasck.exports.max-artifact-bytes:52428800}") long maxExportArtifactBytes,
            @Value("${trasck.exports.allowed-content-types:application/json,text/csv,application/octet-stream}") String exportContentTypes,
            @Value("${trasck.imports.max-parse-bytes:5242880}") long maxImportParseBytes,
            @Value("${trasck.imports.allowed-content-types:text/csv,application/csv,application/json,text/json,text/plain}") String importContentTypes
    ) {
        this.policyRepository = policyRepository;
        this.projectPolicyRepository = projectPolicyRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.defaults = new ContentLimits(
                positive(maxAttachmentUploadBytes),
                positive(maxAttachmentDownloadBytes),
                normalizeCsv(attachmentContentTypes),
                positive(maxExportArtifactBytes),
                normalizeCsv(exportContentTypes),
                positive(maxImportParseBytes),
                normalizeCsv(importContentTypes)
        );
    }

    @Transactional(readOnly = true)
    public WorkspaceSecurityPolicyResponse getWorkspacePolicy(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        WorkspaceSecurityPolicy policy = policyRepository.findById(workspaceId).orElse(null);
        return response(workspace, policy);
    }

    @Transactional
    public WorkspaceSecurityPolicyResponse updateWorkspacePolicy(UUID workspaceId, WorkspaceSecurityPolicyRequest request) {
        WorkspaceSecurityPolicyRequest updateRequest = request == null
                ? new WorkspaceSecurityPolicyRequest(null, null, null, null, null, null, null, null, null)
                : request;
        UUID actorId = currentUserService.requireUserId();
        Workspace workspace = activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        if (updateRequest.anonymousReadEnabled() != null) {
            workspace.setAnonymousReadEnabled(updateRequest.anonymousReadEnabled());
            workspace = workspaceRepository.save(workspace);
        }
        WorkspaceSecurityPolicy policy = policyRepository.findById(workspaceId).orElse(null);
        if (hasContentPolicyUpdate(updateRequest)) {
            WorkspaceSecurityPolicy updatedPolicy = policy == null ? new WorkspaceSecurityPolicy() : policy;
            updatedPolicy.setWorkspaceId(workspaceId);
            updatedPolicy.setAttachmentMaxUploadBytes(nullablePositive(updateRequest.attachmentMaxUploadBytes(), "attachmentMaxUploadBytes"));
            updatedPolicy.setAttachmentMaxDownloadBytes(nullablePositive(updateRequest.attachmentMaxDownloadBytes(), "attachmentMaxDownloadBytes"));
            updatedPolicy.setAttachmentAllowedContentTypes(nullableCsv(updateRequest.attachmentAllowedContentTypes()));
            updatedPolicy.setExportMaxArtifactBytes(nullablePositive(updateRequest.exportMaxArtifactBytes(), "exportMaxArtifactBytes"));
            updatedPolicy.setExportAllowedContentTypes(nullableCsv(updateRequest.exportAllowedContentTypes()));
            updatedPolicy.setImportMaxParseBytes(nullablePositive(updateRequest.importMaxParseBytes(), "importMaxParseBytes"));
            updatedPolicy.setImportAllowedContentTypes(nullableCsv(updateRequest.importAllowedContentTypes()));
            policy = policyRepository.save(updatedPolicy);
        }
        return response(workspace, policy);
    }

    @Transactional(readOnly = true)
    public ProjectSecurityPolicyResponse getProjectPolicy(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.admin");
        ProjectSecurityPolicy policy = projectPolicyRepository.findById(project.getId()).orElse(null);
        return projectResponse(project, policy);
    }

    @Transactional
    public ProjectSecurityPolicyResponse updateProjectPolicy(UUID projectId, WorkspaceSecurityPolicyRequest request) {
        WorkspaceSecurityPolicyRequest updateRequest = request == null
                ? new WorkspaceSecurityPolicyRequest(null, null, null, null, null, null, null, null, null)
                : request;
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.admin");
        if (updateRequest.visibility() != null) {
            project.setVisibility(normalizeProjectVisibility(updateRequest.visibility()));
            project = projectRepository.save(project);
        }
        ProjectSecurityPolicy policy = projectPolicyRepository.findById(project.getId()).orElse(null);
        if (hasContentPolicyUpdate(updateRequest)) {
            ProjectSecurityPolicy updatedPolicy = policy == null ? new ProjectSecurityPolicy() : policy;
            updatedPolicy.setProjectId(project.getId());
            updatedPolicy.setAttachmentMaxUploadBytes(nullablePositive(updateRequest.attachmentMaxUploadBytes(), "attachmentMaxUploadBytes"));
            updatedPolicy.setAttachmentMaxDownloadBytes(nullablePositive(updateRequest.attachmentMaxDownloadBytes(), "attachmentMaxDownloadBytes"));
            updatedPolicy.setAttachmentAllowedContentTypes(nullableCsv(updateRequest.attachmentAllowedContentTypes()));
            updatedPolicy.setExportMaxArtifactBytes(nullablePositive(updateRequest.exportMaxArtifactBytes(), "exportMaxArtifactBytes"));
            updatedPolicy.setExportAllowedContentTypes(nullableCsv(updateRequest.exportAllowedContentTypes()));
            updatedPolicy.setImportMaxParseBytes(nullablePositive(updateRequest.importMaxParseBytes(), "importMaxParseBytes"));
            updatedPolicy.setImportAllowedContentTypes(nullableCsv(updateRequest.importAllowedContentTypes()));
            policy = projectPolicyRepository.save(updatedPolicy);
        }
        return projectResponse(project, policy);
    }

    @Transactional(readOnly = true)
    public ContentLimits limits(UUID workspaceId) {
        return limits(workspaceId, null);
    }

    @Transactional(readOnly = true)
    public ContentLimits limits(UUID workspaceId, UUID projectId) {
        WorkspaceSecurityPolicy policy = workspaceId == null ? null : policyRepository.findById(workspaceId).orElse(null);
        ProjectSecurityPolicy projectPolicy = projectId == null ? null : projectPolicyRepository.findById(projectId).orElse(null);
        ContentLimits workspaceLimits = merge(defaults, policy);
        return merge(workspaceLimits, projectPolicy);
    }

    private ContentLimits merge(ContentLimits base, WorkspaceSecurityPolicy policy) {
        return new ContentLimits(
                policy == null || policy.getAttachmentMaxUploadBytes() == null ? base.attachmentMaxUploadBytes() : policy.getAttachmentMaxUploadBytes(),
                policy == null || policy.getAttachmentMaxDownloadBytes() == null ? base.attachmentMaxDownloadBytes() : policy.getAttachmentMaxDownloadBytes(),
                policy == null || policy.getAttachmentAllowedContentTypes() == null ? base.attachmentAllowedContentTypes() : policy.getAttachmentAllowedContentTypes(),
                policy == null || policy.getExportMaxArtifactBytes() == null ? base.exportMaxArtifactBytes() : policy.getExportMaxArtifactBytes(),
                policy == null || policy.getExportAllowedContentTypes() == null ? base.exportAllowedContentTypes() : policy.getExportAllowedContentTypes(),
                policy == null || policy.getImportMaxParseBytes() == null ? base.importMaxParseBytes() : policy.getImportMaxParseBytes(),
                policy == null || policy.getImportAllowedContentTypes() == null ? base.importAllowedContentTypes() : policy.getImportAllowedContentTypes()
        );
    }

    private ContentLimits merge(ContentLimits base, ProjectSecurityPolicy policy) {
        return new ContentLimits(
                policy == null || policy.getAttachmentMaxUploadBytes() == null ? base.attachmentMaxUploadBytes() : policy.getAttachmentMaxUploadBytes(),
                policy == null || policy.getAttachmentMaxDownloadBytes() == null ? base.attachmentMaxDownloadBytes() : policy.getAttachmentMaxDownloadBytes(),
                policy == null || policy.getAttachmentAllowedContentTypes() == null ? base.attachmentAllowedContentTypes() : policy.getAttachmentAllowedContentTypes(),
                policy == null || policy.getExportMaxArtifactBytes() == null ? base.exportMaxArtifactBytes() : policy.getExportMaxArtifactBytes(),
                policy == null || policy.getExportAllowedContentTypes() == null ? base.exportAllowedContentTypes() : policy.getExportAllowedContentTypes(),
                policy == null || policy.getImportMaxParseBytes() == null ? base.importMaxParseBytes() : policy.getImportMaxParseBytes(),
                policy == null || policy.getImportAllowedContentTypes() == null ? base.importAllowedContentTypes() : policy.getImportAllowedContentTypes()
        );
    }

    private WorkspaceSecurityPolicyResponse response(Workspace workspace, WorkspaceSecurityPolicy policy) {
        UUID workspaceId = workspace.getId();
        ContentLimits effective = limits(workspaceId);
        return new WorkspaceSecurityPolicyResponse(
                workspaceId,
                Boolean.TRUE.equals(workspace.getAnonymousReadEnabled()),
                effective.attachmentMaxUploadBytes(),
                effective.attachmentMaxDownloadBytes(),
                effective.attachmentAllowedContentTypes(),
                effective.exportMaxArtifactBytes(),
                effective.exportAllowedContentTypes(),
                effective.importMaxParseBytes(),
                effective.importAllowedContentTypes(),
                policy != null,
                policy == null ? null : policy.getCreatedAt(),
                policy == null ? null : policy.getUpdatedAt()
        );
    }

    private ProjectSecurityPolicyResponse projectResponse(Project project, ProjectSecurityPolicy policy) {
        Workspace workspace = activeWorkspace(project.getWorkspaceId());
        ContentLimits effective = limits(project.getWorkspaceId(), project.getId());
        boolean workspaceAnonymousReadEnabled = Boolean.TRUE.equals(workspace.getAnonymousReadEnabled());
        String visibility = project.getVisibility();
        return new ProjectSecurityPolicyResponse(
                project.getId(),
                project.getWorkspaceId(),
                visibility,
                workspaceAnonymousReadEnabled,
                workspaceAnonymousReadEnabled && "public".equals(visibility),
                effective.attachmentMaxUploadBytes(),
                effective.attachmentMaxDownloadBytes(),
                effective.attachmentAllowedContentTypes(),
                effective.exportMaxArtifactBytes(),
                effective.exportAllowedContentTypes(),
                effective.importMaxParseBytes(),
                effective.importAllowedContentTypes(),
                policyRepository.existsById(project.getWorkspaceId()),
                policy != null,
                policy == null ? null : policy.getCreatedAt(),
                policy == null ? null : policy.getUpdatedAt()
        );
    }

    private boolean hasContentPolicyUpdate(WorkspaceSecurityPolicyRequest request) {
        return request.attachmentMaxUploadBytes() != null
                || request.attachmentMaxDownloadBytes() != null
                || request.attachmentAllowedContentTypes() != null
                || request.exportMaxArtifactBytes() != null
                || request.exportAllowedContentTypes() != null
                || request.importMaxParseBytes() != null
                || request.importAllowedContentTypes() != null;
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(required(workspaceId, "workspaceId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
        return workspace;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(required(projectId, "projectId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return project;
    }

    private UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private long positive(long value) {
        return Math.max(1, value);
    }

    private Long nullablePositive(Long value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than 0");
        }
        return value;
    }

    private String nullableCsv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeCsv(value);
        if (normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private String normalizeCsv(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
                .map(this::normalizeContentType)
                .filter(type -> !type.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String normalizeContentType(String value) {
        if (value == null) {
            return "";
        }
        int separator = value.indexOf(';');
        String contentType = separator < 0 ? value : value.substring(0, separator);
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int slash = normalized.indexOf('/');
        boolean invalid = !normalized.isBlank()
                && (slash <= 0
                || slash == normalized.length() - 1
                || normalized.indexOf('/', slash + 1) >= 0
                || normalized.chars().anyMatch(Character::isWhitespace));
        if (invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content types must use type/subtype format");
        }
        return normalized;
    }

    private String normalizeProjectVisibility(String visibility) {
        String normalized = visibility == null ? "" : visibility.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("private", "workspace", "public").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility must be private, workspace, or public");
        }
        return normalized;
    }
}
