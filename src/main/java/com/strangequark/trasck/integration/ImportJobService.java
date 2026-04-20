package com.strangequark.trasck.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workitem.WorkItemCreateRequest;
import com.strangequark.trasck.workitem.WorkItemResponse;
import com.strangequark.trasck.workitem.WorkItemService;
import com.strangequark.trasck.workitem.WorkItemUpdateRequest;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final ImportMappingTemplateRepository importMappingTemplateRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemService workItemService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public ImportJobService(
            ObjectMapper objectMapper,
            ImportJobRepository importJobRepository,
            ImportJobRecordRepository importJobRecordRepository,
            ImportMappingTemplateRepository importMappingTemplateRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkItemService workItemService,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.importJobRepository = importJobRepository;
        this.importJobRecordRepository = importJobRecordRepository;
        this.importMappingTemplateRepository = importMappingTemplateRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workItemService = workItemService;
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

    @Transactional(readOnly = true)
    public List<ImportMappingTemplateResponse> listMappingTemplates(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        return importMappingTemplateRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(ImportMappingTemplateResponse::from)
                .toList();
    }

    @Transactional
    public ImportMappingTemplateResponse createMappingTemplate(UUID workspaceId, ImportMappingTemplateRequest request) {
        ImportMappingTemplateRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        ImportMappingTemplate template = new ImportMappingTemplate();
        template.setWorkspaceId(workspaceId);
        applyMappingTemplateRequest(template, createRequest, true);
        ImportMappingTemplate saved = importMappingTemplateRepository.save(template);
        recordMappingTemplateEvent(saved, "import_mapping_template.created", actorId);
        return ImportMappingTemplateResponse.from(saved);
    }

    @Transactional
    public ImportMappingTemplateResponse updateMappingTemplate(UUID mappingTemplateId, ImportMappingTemplateRequest request) {
        ImportMappingTemplateRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        applyMappingTemplateRequest(template, updateRequest, false);
        ImportMappingTemplate saved = importMappingTemplateRepository.save(template);
        recordMappingTemplateEvent(saved, "import_mapping_template.updated", actorId);
        return ImportMappingTemplateResponse.from(saved);
    }

    @Transactional
    public void deleteMappingTemplate(UUID mappingTemplateId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        template.setEnabled(false);
        importMappingTemplateRepository.save(template);
        recordMappingTemplateEvent(template, "import_mapping_template.disabled", actorId);
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

    @Transactional
    public ImportParseResponse parse(UUID importJobId, ImportParseRequest request) {
        ImportParseRequest parseRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        String content = requiredText(parseRequest.content(), "content");
        List<ParsedImportRecord> parsed = parseRecords(job.getProvider(), parseRequest.sourceType(), content);
        List<ImportJobRecordResponse> responses = new ArrayList<>();
        for (ParsedImportRecord parsedRecord : parsed) {
            ImportJobRecord record = importJobRecordRepository
                    .findByImportJobIdAndSourceTypeAndSourceId(job.getId(), parsedRecord.sourceType(), parsedRecord.sourceId())
                    .orElseGet(ImportJobRecord::new);
            if (record.getId() == null) {
                record.setImportJobId(job.getId());
                record.setSourceType(parsedRecord.sourceType());
                record.setSourceId(parsedRecord.sourceId());
            }
            record.setStatus("pending");
            record.setRawPayload(parsedRecord.rawPayload());
            ImportJobRecord saved = importJobRecordRepository.save(record);
            responses.add(ImportJobRecordResponse.from(saved));
        }
        if ("queued".equals(job.getStatus())) {
            job.setStatus("running");
            job.setStartedAt(OffsetDateTime.now());
            importJobRepository.save(job);
        }
        recordJobEvent(job, "import_job.parsed", actorId);
        return new ImportParseResponse(job.getId(), job.getProvider(), responses.size(), responses);
    }

    @Transactional
    public ImportMaterializeResponse materialize(UUID importJobId, ImportMaterializeRequest request) {
        ImportMaterializeRequest materializeRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        ImportMappingTemplate template = importMappingTemplateRepository
                .findByIdAndWorkspaceId(required(materializeRequest.mappingTemplateId(), "mappingTemplateId"), job.getWorkspaceId())
                .orElseThrow(() -> badRequest("Mapping template not found in this workspace"));
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw badRequest("Mapping template is disabled");
        }
        if (!job.getProvider().equals(template.getProvider())) {
            throw badRequest("Mapping template provider must match the import job provider");
        }
        UUID projectId = materializeRequest.projectId() == null ? template.getProjectId() : materializeRequest.projectId();
        Project project = activeProjectInWorkspace(projectId, job.getWorkspaceId());
        int limit = normalizeMaterializeLimit(materializeRequest.limit());
        boolean updateExisting = Boolean.TRUE.equals(materializeRequest.updateExisting());
        List<ImportJobRecord> candidates = importJobRecordRepository
                .findByImportJobIdAndStatusInOrderBySourceTypeAscSourceIdAsc(job.getId(), updateExisting ? List.of("pending", "failed", "imported") : List.of("pending", "failed"));
        List<ImportJobRecordResponse> responses = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        int processed = 0;
        for (ImportJobRecord record : candidates) {
            if (processed >= limit) {
                break;
            }
            if (hasText(template.getSourceType()) && !template.getSourceType().equalsIgnoreCase(record.getSourceType())) {
                continue;
            }
            processed++;
            if (record.getTargetId() != null && !updateExisting) {
                record.setStatus("imported");
                record.setErrorMessage(null);
                responses.add(ImportJobRecordResponse.from(importJobRecordRepository.save(record)));
                continue;
            }
            try {
                MaterializedWorkItem workItem = materializeRecord(job, template, project, record, updateExisting);
                if (workItem.created()) {
                    created++;
                } else {
                    updated++;
                }
                record.setTargetType("work_item");
                record.setTargetId(workItem.response().id());
                record.setStatus("imported");
                record.setErrorMessage(null);
            } catch (ResponseStatusException ex) {
                failed++;
                record.setStatus("failed");
                record.setErrorMessage(ex.getReason());
            } catch (RuntimeException ex) {
                failed++;
                record.setStatus("failed");
                record.setErrorMessage(ex.getMessage());
            }
            responses.add(ImportJobRecordResponse.from(importJobRecordRepository.save(record)));
        }
        recordJobEvent(job, "import_job.materialized", actorId);
        return new ImportMaterializeResponse(job.getId(), template.getId(), project.getId(), processed, created, updated, failed, responses);
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

    private void applyMappingTemplateRequest(ImportMappingTemplate template, ImportMappingTemplateRequest request, boolean create) {
        if (create || hasText(request.name())) {
            template.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.provider())) {
            template.setProvider(requiredText(request.provider(), "provider").toLowerCase());
        }
        if (create || request.sourceType() != null) {
            template.setSourceType(hasText(request.sourceType()) ? request.sourceType().trim() : null);
        }
        if (create || hasText(request.targetType())) {
            String targetType = hasText(request.targetType()) ? request.targetType().trim().toLowerCase() : "work_item";
            if (!"work_item".equals(targetType)) {
                throw badRequest("targetType must be work_item");
            }
            template.setTargetType(targetType);
        }
        if (create || request.projectId() != null) {
            UUID projectId = request.projectId();
            template.setProjectId(projectId == null ? null : activeProjectInWorkspace(projectId, template.getWorkspaceId()).getId());
        }
        if (create || request.workItemTypeKey() != null) {
            template.setWorkItemTypeKey(hasText(request.workItemTypeKey()) ? request.workItemTypeKey().trim() : null);
        }
        if (create || request.statusKey() != null) {
            template.setStatusKey(hasText(request.statusKey()) ? request.statusKey().trim() : null);
        }
        if (create || request.fieldMapping() != null) {
            template.setFieldMapping(toJsonObject(request.fieldMapping()));
        }
        if (create || request.defaults() != null) {
            template.setDefaults(toJsonObject(request.defaults()));
        }
        if (request.enabled() != null) {
            template.setEnabled(request.enabled());
        } else if (create) {
            template.setEnabled(true);
        }
    }

    private MaterializedWorkItem materializeRecord(
            ImportJob job,
            ImportMappingTemplate template,
            Project project,
            ImportJobRecord record,
            boolean updateExisting
    ) {
        JsonNode rawPayload = record.getRawPayload() == null ? objectMapper.createObjectNode() : record.getRawPayload();
        JsonNode mapping = template.getFieldMapping() == null ? objectMapper.createObjectNode() : template.getFieldMapping();
        JsonNode defaults = template.getDefaults() == null ? objectMapper.createObjectNode() : template.getDefaults();
        if (record.getTargetId() != null && updateExisting) {
            WorkItemUpdateRequest updateRequest = new WorkItemUpdateRequest(
                    null,
                    textValue(rawPayload, mapping, defaults, "typeKey", null),
                    null,
                    null,
                    null,
                    textValue(rawPayload, mapping, defaults, "priorityKey", null),
                    null,
                    null,
                    textValue(rawPayload, mapping, defaults, "title", null),
                    textValue(rawPayload, mapping, defaults, "descriptionMarkdown", null),
                    null,
                    textValue(rawPayload, mapping, defaults, "visibility", null),
                    null,
                    null,
                    null,
                    null,
                    null,
                    objectValue(rawPayload, mapping, defaults, "customFields")
            );
            return new MaterializedWorkItem(workItemService.update(record.getTargetId(), updateRequest), false);
        }
        String typeKey = firstText(
                textValue(rawPayload, mapping, defaults, "typeKey", null),
                template.getWorkItemTypeKey(),
                defaultText(defaults, "typeKey")
        );
        String statusKey = firstText(
                textValue(rawPayload, mapping, defaults, "statusKey", null),
                template.getStatusKey(),
                defaultText(defaults, "statusKey")
        );
        WorkItemCreateRequest createRequest = new WorkItemCreateRequest(
                null,
                typeKey,
                null,
                null,
                statusKey,
                null,
                textValue(rawPayload, mapping, defaults, "priorityKey", null),
                null,
                null,
                null,
                requiredText(textValue(rawPayload, mapping, defaults, "title", null), "mapped title"),
                textValue(rawPayload, mapping, defaults, "descriptionMarkdown", "Imported from " + job.getProvider() + " " + record.getSourceId()),
                null,
                textValue(rawPayload, mapping, defaults, "visibility", null),
                null,
                null,
                null,
                null,
                null,
                objectValue(rawPayload, mapping, defaults, "customFields")
        );
        return new MaterializedWorkItem(workItemService.create(project.getId(), createRequest), true);
    }

    private String textValue(JsonNode rawPayload, JsonNode mapping, JsonNode defaults, String targetField, String fallback) {
        JsonNode mapped = mappedNode(rawPayload, mapping, targetField);
        if (hasTextNode(mapped)) {
            return mapped.isValueNode() ? mapped.asText().trim() : mapped.toString();
        }
        String defaultValue = defaultText(defaults, targetField);
        return hasText(defaultValue) ? defaultValue : fallback;
    }

    private Object objectValue(JsonNode rawPayload, JsonNode mapping, JsonNode defaults, String targetField) {
        JsonNode mapped = mappedNode(rawPayload, mapping, targetField);
        if (mapped != null && !mapped.isMissingNode() && !mapped.isNull()) {
            return mapped;
        }
        JsonNode defaultValue = defaults == null ? null : defaults.get(targetField);
        return defaultValue == null || defaultValue.isNull() ? null : defaultValue;
    }

    private JsonNode mappedNode(JsonNode rawPayload, JsonNode mapping, String targetField) {
        if (mapping == null || !mapping.has(targetField)) {
            return null;
        }
        JsonNode source = mapping.get(targetField);
        if (source.isArray()) {
            for (JsonNode candidate : source) {
                JsonNode value = nodeAtPath(rawPayload, candidate.asText());
                if (hasTextNode(value)) {
                    return value;
                }
            }
            return null;
        }
        if (source.isTextual()) {
            return nodeAtPath(rawPayload, source.asText());
        }
        if (source.isObject() && source.hasNonNull("path")) {
            return nodeAtPath(rawPayload, source.get("path").asText());
        }
        if (source.isObject() && source.has("value")) {
            return source.get("value");
        }
        return source;
    }

    private JsonNode nodeAtPath(JsonNode rawPayload, String path) {
        if (!hasText(path) || rawPayload == null) {
            return null;
        }
        JsonNode current = rawPayload;
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        if (normalized.startsWith("/")) {
            JsonNode value = rawPayload.at(normalized);
            return value.isMissingNode() ? null : value;
        }
        for (String segment : normalized.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            if (current.isArray()) {
                try {
                    current = current.get(Integer.parseInt(segment));
                } catch (NumberFormatException ex) {
                    return null;
                }
            } else {
                current = current.get(segment);
            }
        }
        return current;
    }

    private String defaultText(JsonNode defaults, String fieldName) {
        return defaults != null && defaults.hasNonNull(fieldName) ? defaults.get(fieldName).asText() : null;
    }

    private boolean hasTextNode(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() && hasText(node.isValueNode() ? node.asText() : node.toString());
    }

    private ImportJobResponse response(ImportJob job) {
        return ImportJobResponse.from(job, importJobRecordRepository.findByImportJobIdOrderBySourceTypeAscSourceIdAsc(job.getId()));
    }

    private List<ParsedImportRecord> parseRecords(String provider, String sourceType, String content) {
        return switch (provider) {
            case "csv" -> parseCsv(sourceType, content);
            case "jira" -> parseJsonImport(content, sourceType == null || sourceType.isBlank() ? "issue" : sourceType, List.of("key", "id"));
            case "rally" -> parseJsonImport(content, sourceType == null || sourceType.isBlank() ? "artifact" : sourceType, List.of("FormattedID", "ObjectID", "_refObjectUUID", "id"));
            default -> throw badRequest("Unsupported import parser provider: " + provider);
        };
    }

    private List<ParsedImportRecord> parseCsv(String sourceType, String content) {
        List<String> lines = content.lines()
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.size() < 2) {
            throw badRequest("CSV content must include a header row and at least one data row");
        }
        List<String> headers = splitCsvLine(lines.get(0));
        if (headers.isEmpty()) {
            throw badRequest("CSV header row is empty");
        }
        List<ParsedImportRecord> records = new ArrayList<>();
        String normalizedSourceType = hasText(sourceType) ? sourceType.trim() : "row";
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = splitCsvLine(lines.get(i));
            ObjectNode payload = objectMapper.createObjectNode();
            for (int column = 0; column < headers.size(); column++) {
                String value = column < values.size() ? values.get(column) : "";
                payload.put(headers.get(column), value);
            }
            String sourceId = firstText(payload, List.of("key", "id", "issue_key", "formatted_id", "FormattedID"));
            if (!hasText(sourceId)) {
                sourceId = Integer.toString(i);
            }
            records.add(new ParsedImportRecord(normalizedSourceType, sourceId, payload));
        }
        return records;
    }

    private List<ParsedImportRecord> parseJsonImport(String content, String sourceType, List<String> idFields) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception ex) {
            throw badRequest("JSON import content is invalid");
        }
        JsonNode recordsNode = unwrapJsonRecords(root);
        if (!recordsNode.isArray()) {
            throw badRequest("JSON import content must be an array or contain issues/results/records");
        }
        List<ParsedImportRecord> records = new ArrayList<>();
        for (JsonNode item : recordsNode) {
            if (!item.isObject()) {
                throw badRequest("JSON import records must be objects");
            }
            String sourceId = firstText(item, idFields);
            if (!hasText(sourceId)) {
                throw badRequest("JSON import records must include one of " + idFields);
            }
            records.add(new ParsedImportRecord(sourceType, sourceId, item));
        }
        return records;
    }

    private JsonNode unwrapJsonRecords(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        for (String field : List.of("issues", "results", "records", "items")) {
            if (root.has(field) && root.get(field).isArray()) {
                return root.get(field);
            }
        }
        ArrayNode single = objectMapper.createArrayNode();
        if (root.isObject()) {
            single.add(root);
        }
        return single;
    }

    private List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private String firstText(JsonNode node, List<String> fields) {
        for (String field : fields) {
            if (node.hasNonNull(field) && !node.get(field).asText().isBlank()) {
                return node.get(field).asText();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private ImportJob importJob(UUID importJobId) {
        return importJobRepository.findById(importJobId).orElseThrow(() -> notFound("Import job not found"));
    }

    private ImportMappingTemplate mappingTemplate(UUID mappingTemplateId) {
        return importMappingTemplateRepository.findById(mappingTemplateId).orElseThrow(() -> notFound("Import mapping template not found"));
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

    private Project activeProjectInWorkspace(UUID projectId, UUID workspaceId) {
        if (projectId == null) {
            throw badRequest("projectId is required");
        }
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> badRequest("Project not found in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("Project not found in this workspace");
        }
        return project;
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

    private void recordMappingTemplateEvent(ImportMappingTemplate template, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("mappingTemplateId", template.getId().toString())
                .put("name", template.getName())
                .put("provider", template.getProvider())
                .put("actorUserId", actorId.toString());
        domainEventService.record(template.getWorkspaceId(), "import_mapping_template", template.getId(), eventType, payload);
    }

    private int normalizeMaterializeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        if (limit < 1 || limit > 200) {
            throw badRequest("limit must be between 1 and 200");
        }
        return limit;
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

    private record ParsedImportRecord(String sourceType, String sourceId, JsonNode rawPayload) {
    }

    private record MaterializedWorkItem(WorkItemResponse response, boolean created) {
    }
}
