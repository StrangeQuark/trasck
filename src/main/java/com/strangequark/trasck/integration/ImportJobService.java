package com.strangequark.trasck.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.JsonValues;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.activity.Attachment;
import com.strangequark.trasck.activity.AttachmentRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.activity.storage.AttachmentStorageService;
import com.strangequark.trasck.activity.storage.AttachmentUpload;
import com.strangequark.trasck.activity.storage.StoredAttachment;
import com.strangequark.trasck.automation.AutomationWorkerHealth;
import com.strangequark.trasck.automation.AutomationWorkerHealthId;
import com.strangequark.trasck.automation.AutomationWorkerHealthRepository;
import com.strangequark.trasck.automation.AutomationWorkerRun;
import com.strangequark.trasck.automation.AutomationWorkerRunRepository;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportJobService {

    private static final String COMPLETE_OPEN_CONFLICTS_CONFIRMATION = "COMPLETE WITH OPEN CONFLICTS";
    private static final String RESOLVE_FILTERED_CONFLICTS_CONFIRMATION = "RESOLVE FILTERED CONFLICTS";
    private static final int DEFAULT_CONFLICT_PREVIEW_PAGE_SIZE = 50;
    private static final int MAX_CONFLICT_PREVIEW_PAGE_SIZE = 200;
    private static final int MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE = 500;
    private static final int DEFAULT_CONFLICT_RESOLUTION_WORKER_LIMIT = 10;
    private static final int MAX_CONFLICT_RESOLUTION_WORKER_LIMIT = 50;
    private static final String IMPORT_CONFLICT_RESOLUTION_WORKER_TYPE = "import_conflict_resolution";
    private static final DateTimeFormatter EXPORT_FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Set<String> SUPPORTED_TRANSFORM_FUNCTIONS = Set.of(
            "trim",
            "lower",
            "lowercase",
            "upper",
            "uppercase",
            "collapse_whitespace",
            "replace",
            "prefix",
            "prepend",
            "suffix",
            "append",
            "truncate",
            "substring"
    );

    private static final List<ImportSampleDefinition> IMPORT_SAMPLES = List.of(
            new ImportSampleDefinition(
                    "csv",
                    "CSV sample",
                    "csv",
                    "row",
                    "Small CSV job with story, bug, status, visibility, and description fields.",
                    "visibility",
                    """
                    id,title,type,status,visibility,description
                    CSV-1,"  Imported: CSV    story  ",Story,To Do,Public,"  First CSV story imported through Trasck.  "
                    CSV-2,"Imported: CSV bug",Bug,In Progress,Private,"Bug row that should map to a Trasck bug."
                    CSV-3,"Imported: CSV done story",Story,Done,Public,"Completed source item for status translation checks."
                    """
            ),
            new ImportSampleDefinition(
                    "jira",
                    "Jira sample",
                    "jira",
                    "issue",
                    "Jira-shaped JSON job with issue summaries, types, statuses, security, and descriptions.",
                    "fields.security",
                    """
                    {
                      "issues": [
                        {
                          "key": "JIRA-1",
                          "fields": {
                            "summary": "  Imported: Jira    story  ",
                            "issuetype": { "name": "Story" },
                            "status": { "name": "To Do" },
                            "security": "Public",
                            "description": "  Jira story that should trim and collapse whitespace.  "
                          }
                        },
                        {
                          "key": "JIRA-2",
                          "fields": {
                            "summary": "Imported: Jira bug",
                            "issuetype": { "name": "Bug" },
                            "status": { "name": "In Progress" },
                            "security": "Private",
                            "description": "Jira bug for type and status translation."
                          }
                        },
                        {
                          "key": "JIRA-3",
                          "fields": {
                            "summary": "Imported: Jira accepted story",
                            "issuetype": { "name": "Story" },
                            "status": { "name": "Done" },
                            "security": "Public",
                            "description": "Source item that should map to a done status."
                          }
                        }
                      ]
                    }
                    """
            ),
            new ImportSampleDefinition(
                    "rally",
                    "Rally sample",
                    "rally",
                    "artifact",
                    "Rally-shaped JSON job with user stories, defects, schedule states, and descriptions.",
                    "Visibility",
                    """
                    {
                      "results": [
                        {
                          "_refObjectUUID": "rally-story-1",
                          "FormattedID": "US101",
                          "Name": "  Imported: Rally    story  ",
                          "_type": "HierarchicalRequirement",
                          "ScheduleState": "Defined",
                          "Visibility": "Public",
                          "Description": "  Rally user story for transform and lookup testing.  "
                        },
                        {
                          "_refObjectUUID": "rally-defect-1",
                          "FormattedID": "DE102",
                          "Name": "Imported: Rally defect",
                          "_type": "Defect",
                          "ScheduleState": "In-Progress",
                          "Visibility": "Private",
                          "Description": "Rally defect for bug translation."
                        },
                        {
                          "_refObjectUUID": "rally-story-2",
                          "FormattedID": "US103",
                          "Name": "Imported: Rally accepted story",
                          "_type": "HierarchicalRequirement",
                          "ScheduleState": "Accepted",
                          "Visibility": "Public",
                          "Description": "Rally item for closed status mapping."
                        }
                      ]
                    }
                    """
            )
    );

    private final ObjectMapper objectMapper;
    private final ImportJobRepository importJobRepository;
    private final ImportJobRecordRepository importJobRecordRepository;
    private final ImportJobRecordVersionRepository importJobRecordVersionRepository;
    private final ImportConflictResolutionJobRepository importConflictResolutionJobRepository;
    private final ImportMappingTemplateRepository importMappingTemplateRepository;
    private final ImportTransformPresetRepository importTransformPresetRepository;
    private final ImportTransformPresetVersionRepository importTransformPresetVersionRepository;
    private final ImportMaterializationRunRepository importMaterializationRunRepository;
    private final ImportMappingValueLookupRepository importMappingValueLookupRepository;
    private final ImportMappingTypeTranslationRepository importMappingTypeTranslationRepository;
    private final ImportMappingStatusTranslationRepository importMappingStatusTranslationRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final ExportJobRepository exportJobRepository;
    private final AutomationWorkerRunRepository automationWorkerRunRepository;
    private final AutomationWorkerHealthRepository automationWorkerHealthRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemService workItemService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;
    private final Environment environment;
    private final boolean sampleJobsEnabled;

    public ImportJobService(
            ObjectMapper objectMapper,
            ImportJobRepository importJobRepository,
            ImportJobRecordRepository importJobRecordRepository,
            ImportJobRecordVersionRepository importJobRecordVersionRepository,
            ImportConflictResolutionJobRepository importConflictResolutionJobRepository,
            ImportMappingTemplateRepository importMappingTemplateRepository,
            ImportTransformPresetRepository importTransformPresetRepository,
            ImportTransformPresetVersionRepository importTransformPresetVersionRepository,
            ImportMaterializationRunRepository importMaterializationRunRepository,
            ImportMappingValueLookupRepository importMappingValueLookupRepository,
            ImportMappingTypeTranslationRepository importMappingTypeTranslationRepository,
            ImportMappingStatusTranslationRepository importMappingStatusTranslationRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            AttachmentRepository attachmentRepository,
            AttachmentStorageService attachmentStorageService,
            ExportJobRepository exportJobRepository,
            AutomationWorkerRunRepository automationWorkerRunRepository,
            AutomationWorkerHealthRepository automationWorkerHealthRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkItemService workItemService,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService,
            Environment environment,
            @Value("${trasck.imports.sample-jobs.enabled:false}") boolean sampleJobsEnabled
    ) {
        this.objectMapper = objectMapper;
        this.importJobRepository = importJobRepository;
        this.importJobRecordRepository = importJobRecordRepository;
        this.importJobRecordVersionRepository = importJobRecordVersionRepository;
        this.importConflictResolutionJobRepository = importConflictResolutionJobRepository;
        this.importMappingTemplateRepository = importMappingTemplateRepository;
        this.importTransformPresetRepository = importTransformPresetRepository;
        this.importTransformPresetVersionRepository = importTransformPresetVersionRepository;
        this.importMaterializationRunRepository = importMaterializationRunRepository;
        this.importMappingValueLookupRepository = importMappingValueLookupRepository;
        this.importMappingTypeTranslationRepository = importMappingTypeTranslationRepository;
        this.importMappingStatusTranslationRepository = importMappingStatusTranslationRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.exportJobRepository = exportJobRepository;
        this.automationWorkerRunRepository = automationWorkerRunRepository;
        this.automationWorkerHealthRepository = automationWorkerHealthRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workItemService = workItemService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
        this.environment = environment;
        this.sampleJobsEnabled = sampleJobsEnabled;
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
    public List<ImportSampleResponse> listImportSamples(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        requireSampleJobsEnabled();
        return IMPORT_SAMPLES.stream()
                .map(ImportSampleDefinition::response)
                .toList();
    }

    @Transactional
    public ImportSampleJobResponse createSampleImportJob(UUID workspaceId, String sampleKey, ImportSampleJobRequest request) {
        ImportSampleDefinition sample = importSample(sampleKey);
        ImportSampleJobRequest sampleRequest = request == null ? new ImportSampleJobRequest(null, null) : request;
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        requireSampleJobsEnabled();
        UUID projectId = sampleRequest.projectId();
        boolean createMappingTemplate = Boolean.TRUE.equals(sampleRequest.createMappingTemplate())
                || (sampleRequest.createMappingTemplate() == null && projectId != null);
        if (createMappingTemplate && projectId == null) {
            throw badRequest("projectId is required when createMappingTemplate is true");
        }

        ObjectNode config = objectMapper.createObjectNode()
                .put("demoSample", true)
                .put("sampleKey", sample.key());
        if (projectId != null) {
            activeProjectInWorkspace(projectId, workspaceId);
            config.put("targetProjectId", projectId.toString());
        }

        ImportJobResponse importJob = createImportJob(workspaceId, new ImportJobRequest(sample.provider(), config));
        ImportParseResponse parse = parse(importJob.id(), new ImportParseRequest(sample.content(), null, sample.sourceType()));

        ImportTransformPresetResponse transformPreset = null;
        ImportMappingTemplateResponse mappingTemplate = null;
        if (createMappingTemplate) {
            transformPreset = createSampleTransformPreset(workspaceId, sample);
            mappingTemplate = createSampleMappingTemplate(workspaceId, projectId, sample, transformPreset.id());
        }

        ImportJob savedJob = importJob(importJob.id());
        recordSampleJobEvent(savedJob, sample, actorId, mappingTemplate);
        return new ImportSampleJobResponse(sample.response(), response(savedJob), parse, transformPreset, mappingTemplate);
    }

    private ImportTransformPresetResponse createSampleTransformPreset(UUID workspaceId, ImportSampleDefinition sample) {
        ObjectNode transformationConfig = objectMapper.createObjectNode();
        ArrayNode titleSteps = objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("function", "trim"))
                .add(objectMapper.createObjectNode()
                        .put("function", "replace")
                        .put("target", "Imported: ")
                        .put("replacement", ""))
                .add(objectMapper.createObjectNode().put("function", "collapse_whitespace"));
        transformationConfig.set("title", titleSteps);
        transformationConfig.set("descriptionMarkdown", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("function", "trim")));
        return createTransformPreset(workspaceId, new ImportTransformPresetRequest(
                sample.label() + " demo cleanup " + shortId(),
                "Generated by the Trasck sample import walkthrough.",
                transformationConfig,
                true
        ));
    }

    private ImportMappingTemplateResponse createSampleMappingTemplate(
            UUID workspaceId,
            UUID projectId,
            ImportSampleDefinition sample,
            UUID transformPresetId
    ) {
        ObjectNode fieldMapping = objectMapper.createObjectNode();
        fieldMapping.set("title", textArray("title", "fields.summary", "Name"));
        fieldMapping.set("typeKey", textArray("type", "fields.issuetype.name", "_type"));
        fieldMapping.set("statusKey", textArray("status", "fields.status.name", "ScheduleState"));
        fieldMapping.set("descriptionMarkdown", textArray("description", "fields.description", "Description"));
        ObjectNode defaults = objectMapper.createObjectNode()
                .put("descriptionMarkdown", "Imported through the Trasck sample import walkthrough.");
        ImportMappingTemplateResponse mapping = createMappingTemplate(workspaceId, new ImportMappingTemplateRequest(
                sample.label() + " demo mapping " + shortId(),
                sample.provider(),
                sample.sourceType(),
                "work_item",
                projectId,
                "story",
                null,
                transformPresetId,
                null,
                fieldMapping,
                defaults,
                objectMapper.createObjectNode(),
                true
        ));

        createSampleTypeTranslations(mapping.id());
        createSampleStatusTranslations(mapping.id());
        createValueLookup(mapping.id(), new ImportMappingValueLookupRequest(
                sample.visibilityPath(),
                "Public",
                "visibility",
                "public",
                0,
                true
        ));
        return mapping;
    }

    private void createSampleTypeTranslations(UUID mappingTemplateId) {
        createTypeTranslation(mappingTemplateId, new ImportMappingTypeTranslationRequest("Story", "story", true));
        createTypeTranslation(mappingTemplateId, new ImportMappingTypeTranslationRequest("User Story", "story", true));
        createTypeTranslation(mappingTemplateId, new ImportMappingTypeTranslationRequest("HierarchicalRequirement", "story", true));
        createTypeTranslation(mappingTemplateId, new ImportMappingTypeTranslationRequest("Bug", "bug", true));
        createTypeTranslation(mappingTemplateId, new ImportMappingTypeTranslationRequest("Defect", "bug", true));
    }

    private void createSampleStatusTranslations(UUID mappingTemplateId) {
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("To Do", "open", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("Defined", "open", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("Open", "open", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("In Progress", "in_progress", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("In-Progress", "in_progress", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("Done", "done", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("Accepted", "done", true));
        createStatusTranslation(mappingTemplateId, new ImportMappingStatusTranslationRequest("Closed", "done", true));
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

    @Transactional(readOnly = true)
    public List<ImportTransformPresetResponse> listTransformPresets(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        return importTransformPresetRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(ImportTransformPresetResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportTransformPresetResponse getTransformPreset(UUID presetId) {
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset preset = transformPreset(presetId);
        activeWorkspace(preset.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, preset.getWorkspaceId(), "workspace.admin");
        return ImportTransformPresetResponse.from(preset);
    }

    @Transactional(readOnly = true)
    public List<ImportTransformPresetVersionResponse> listTransformPresetVersions(UUID presetId) {
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset preset = transformPreset(presetId);
        activeWorkspace(preset.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, preset.getWorkspaceId(), "workspace.admin");
        return importTransformPresetVersionRepository.findByPresetIdOrderByVersionDesc(preset.getId()).stream()
                .map(ImportTransformPresetVersionResponse::from)
                .toList();
    }

    @Transactional
    public ImportTransformPresetResponse cloneTransformPresetVersion(
            UUID presetId,
            UUID versionId,
            ImportTransformPresetCloneRequest request
    ) {
        ImportTransformPresetCloneRequest cloneRequest = request == null ? new ImportTransformPresetCloneRequest(null, null, null) : request;
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset sourcePreset = transformPreset(presetId);
        ImportTransformPresetVersion version = transformPresetVersion(presetId, versionId);
        activeWorkspace(sourcePreset.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, sourcePreset.getWorkspaceId(), "workspace.admin");
        ImportTransformPreset saved = createTransformPresetClone(sourcePreset, version, cloneRequest.name(), cloneRequest.description(), cloneRequest.enabled());
        recordTransformPresetVersion(saved, "created", actorId);
        recordTransformPresetCloneEvent(saved, sourcePreset.getId(), version, actorId);
        return ImportTransformPresetResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ImportTransformPresetRetargetResponse previewCloneAndRetargetTransformPresetVersion(
            UUID presetId,
            UUID versionId,
            ImportTransformPresetRetargetRequest request
    ) {
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset sourcePreset = transformPreset(presetId);
        ImportTransformPresetVersion version = transformPresetVersion(presetId, versionId);
        activeWorkspace(sourcePreset.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, sourcePreset.getWorkspaceId(), "workspace.admin");
        ImportTransformPresetRetargetRequest retargetRequest = request == null
                ? new ImportTransformPresetRetargetRequest(null, null, null, List.of())
                : request;
        List<ImportMappingTemplate> templates = selectedRetargetTemplates(sourcePreset.getWorkspaceId(), retargetRequest.mappingTemplateIds());
        String cloneName = uniquePresetName(
                sourcePreset.getWorkspaceId(),
                hasText(retargetRequest.name()) ? retargetRequest.name().trim() : version.getName() + " copy"
        );
        String cloneDescription = hasText(retargetRequest.description()) ? retargetRequest.description().trim() : version.getDescription();
        Boolean enabled = retargetRequest.enabled() == null ? Boolean.TRUE.equals(version.getEnabled()) : retargetRequest.enabled();
        return ImportTransformPresetRetargetResponse.preview(
                sourcePreset,
                version,
                cloneName,
                cloneDescription,
                enabled,
                retargetTemplateResponses(templates, null)
        );
    }

    @Transactional
    public ImportTransformPresetRetargetResponse cloneAndRetargetTransformPresetVersion(
            UUID presetId,
            UUID versionId,
            ImportTransformPresetRetargetRequest request
    ) {
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset sourcePreset = transformPreset(presetId);
        ImportTransformPresetVersion version = transformPresetVersion(presetId, versionId);
        activeWorkspace(sourcePreset.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, sourcePreset.getWorkspaceId(), "workspace.admin");
        ImportTransformPresetRetargetRequest retargetRequest = request == null
                ? new ImportTransformPresetRetargetRequest(null, null, null, List.of())
                : request;
        List<ImportMappingTemplate> templates = selectedRetargetTemplates(sourcePreset.getWorkspaceId(), retargetRequest.mappingTemplateIds());
        ImportTransformPreset saved = createTransformPresetClone(sourcePreset, version, retargetRequest.name(), retargetRequest.description(), retargetRequest.enabled());
        recordTransformPresetVersion(saved, "created", actorId);
        recordTransformPresetCloneEvent(saved, sourcePreset.getId(), version, actorId);
        for (ImportMappingTemplate template : templates) {
            template.setTransformPresetId(saved.getId());
            importMappingTemplateRepository.save(template);
            recordMappingTemplateEvent(template, "import_mapping_template.transform_preset_retargeted", actorId);
        }
        recordTransformPresetRetargetEvent(saved, sourcePreset.getId(), version, templates, actorId);
        return ImportTransformPresetRetargetResponse.applied(
                sourcePreset,
                version,
                saved,
                retargetTemplateResponses(templates, saved.getId())
        );
    }

    @Transactional
    public ImportTransformPresetResponse createTransformPreset(UUID workspaceId, ImportTransformPresetRequest request) {
        ImportTransformPresetRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        ImportTransformPreset preset = new ImportTransformPreset();
        preset.setWorkspaceId(workspaceId);
        applyTransformPresetRequest(preset, createRequest, true);
        preset.setVersion(1);
        ImportTransformPreset saved = importTransformPresetRepository.saveAndFlush(preset);
        recordTransformPresetVersion(saved, "created", actorId);
        recordTransformPresetEvent(saved, "import_transform_preset.created", actorId);
        return ImportTransformPresetResponse.from(saved);
    }

    @Transactional
    public ImportTransformPresetResponse updateTransformPreset(UUID presetId, ImportTransformPresetRequest request) {
        ImportTransformPresetRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset preset = transformPreset(presetId);
        permissionService.requireWorkspacePermission(actorId, preset.getWorkspaceId(), "workspace.admin");
        applyTransformPresetRequest(preset, updateRequest, false);
        preset.setVersion(preset.getVersion() == null ? 2 : preset.getVersion() + 1);
        ImportTransformPreset saved = importTransformPresetRepository.saveAndFlush(preset);
        recordTransformPresetVersion(saved, "updated", actorId);
        recordTransformPresetEvent(saved, "import_transform_preset.updated", actorId);
        return ImportTransformPresetResponse.from(saved);
    }

    @Transactional
    public void deleteTransformPreset(UUID presetId) {
        UUID actorId = currentUserService.requireUserId();
        ImportTransformPreset preset = transformPreset(presetId);
        permissionService.requireWorkspacePermission(actorId, preset.getWorkspaceId(), "workspace.admin");
        preset.setEnabled(false);
        preset.setVersion(preset.getVersion() == null ? 2 : preset.getVersion() + 1);
        importTransformPresetRepository.saveAndFlush(preset);
        recordTransformPresetVersion(preset, "disabled", actorId);
        recordTransformPresetEvent(preset, "import_transform_preset.disabled", actorId);
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

    @Transactional(readOnly = true)
    public List<ImportMappingValueLookupResponse> listValueLookups(UUID mappingTemplateId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        return importMappingValueLookupRepository.findByMappingTemplateIdOrderBySortOrderAscSourceFieldAsc(template.getId()).stream()
                .map(ImportMappingValueLookupResponse::from)
                .toList();
    }

    @Transactional
    public ImportMappingValueLookupResponse createValueLookup(UUID mappingTemplateId, ImportMappingValueLookupRequest request) {
        ImportMappingValueLookupRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingValueLookup lookup = new ImportMappingValueLookup();
        lookup.setMappingTemplateId(template.getId());
        applyValueLookupRequest(lookup, createRequest, true);
        ImportMappingValueLookup saved = importMappingValueLookupRepository.save(lookup);
        recordMappingTemplateEvent(template, "import_mapping_lookup.created", actorId);
        return ImportMappingValueLookupResponse.from(saved);
    }

    @Transactional
    public ImportMappingValueLookupResponse updateValueLookup(UUID mappingTemplateId, UUID lookupId, ImportMappingValueLookupRequest request) {
        ImportMappingValueLookupRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingValueLookup lookup = importMappingValueLookupRepository.findByIdAndMappingTemplateId(lookupId, template.getId())
                .orElseThrow(() -> notFound("Import mapping value lookup not found"));
        applyValueLookupRequest(lookup, updateRequest, false);
        ImportMappingValueLookup saved = importMappingValueLookupRepository.save(lookup);
        recordMappingTemplateEvent(template, "import_mapping_lookup.updated", actorId);
        return ImportMappingValueLookupResponse.from(saved);
    }

    @Transactional
    public void deleteValueLookup(UUID mappingTemplateId, UUID lookupId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingValueLookup lookup = importMappingValueLookupRepository.findByIdAndMappingTemplateId(lookupId, template.getId())
                .orElseThrow(() -> notFound("Import mapping value lookup not found"));
        importMappingValueLookupRepository.delete(lookup);
        recordMappingTemplateEvent(template, "import_mapping_lookup.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<ImportMappingTypeTranslationResponse> listTypeTranslations(UUID mappingTemplateId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        return importMappingTypeTranslationRepository.findByMappingTemplateIdOrderBySourceTypeKeyAsc(template.getId()).stream()
                .map(ImportMappingTypeTranslationResponse::from)
                .toList();
    }

    @Transactional
    public ImportMappingTypeTranslationResponse createTypeTranslation(UUID mappingTemplateId, ImportMappingTypeTranslationRequest request) {
        ImportMappingTypeTranslationRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingTypeTranslation translation = new ImportMappingTypeTranslation();
        translation.setMappingTemplateId(template.getId());
        applyTypeTranslationRequest(translation, createRequest, true);
        ImportMappingTypeTranslation saved = importMappingTypeTranslationRepository.save(translation);
        recordMappingTemplateEvent(template, "import_mapping_type_translation.created", actorId);
        return ImportMappingTypeTranslationResponse.from(saved);
    }

    @Transactional
    public ImportMappingTypeTranslationResponse updateTypeTranslation(UUID mappingTemplateId, UUID translationId, ImportMappingTypeTranslationRequest request) {
        ImportMappingTypeTranslationRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingTypeTranslation translation = importMappingTypeTranslationRepository.findByIdAndMappingTemplateId(translationId, template.getId())
                .orElseThrow(() -> notFound("Import mapping type translation not found"));
        applyTypeTranslationRequest(translation, updateRequest, false);
        ImportMappingTypeTranslation saved = importMappingTypeTranslationRepository.save(translation);
        recordMappingTemplateEvent(template, "import_mapping_type_translation.updated", actorId);
        return ImportMappingTypeTranslationResponse.from(saved);
    }

    @Transactional
    public void deleteTypeTranslation(UUID mappingTemplateId, UUID translationId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingTypeTranslation translation = importMappingTypeTranslationRepository.findByIdAndMappingTemplateId(translationId, template.getId())
                .orElseThrow(() -> notFound("Import mapping type translation not found"));
        importMappingTypeTranslationRepository.delete(translation);
        recordMappingTemplateEvent(template, "import_mapping_type_translation.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<ImportMappingStatusTranslationResponse> listStatusTranslations(UUID mappingTemplateId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        return importMappingStatusTranslationRepository.findByMappingTemplateIdOrderBySourceStatusKeyAsc(template.getId()).stream()
                .map(ImportMappingStatusTranslationResponse::from)
                .toList();
    }

    @Transactional
    public ImportMappingStatusTranslationResponse createStatusTranslation(UUID mappingTemplateId, ImportMappingStatusTranslationRequest request) {
        ImportMappingStatusTranslationRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingStatusTranslation translation = new ImportMappingStatusTranslation();
        translation.setMappingTemplateId(template.getId());
        applyStatusTranslationRequest(translation, createRequest, true);
        ImportMappingStatusTranslation saved = importMappingStatusTranslationRepository.save(translation);
        recordMappingTemplateEvent(template, "import_mapping_status_translation.created", actorId);
        return ImportMappingStatusTranslationResponse.from(saved);
    }

    @Transactional
    public ImportMappingStatusTranslationResponse updateStatusTranslation(UUID mappingTemplateId, UUID translationId, ImportMappingStatusTranslationRequest request) {
        ImportMappingStatusTranslationRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingStatusTranslation translation = importMappingStatusTranslationRepository.findByIdAndMappingTemplateId(translationId, template.getId())
                .orElseThrow(() -> notFound("Import mapping status translation not found"));
        applyStatusTranslationRequest(translation, updateRequest, false);
        ImportMappingStatusTranslation saved = importMappingStatusTranslationRepository.save(translation);
        recordMappingTemplateEvent(template, "import_mapping_status_translation.updated", actorId);
        return ImportMappingStatusTranslationResponse.from(saved);
    }

    @Transactional
    public void deleteStatusTranslation(UUID mappingTemplateId, UUID translationId) {
        UUID actorId = currentUserService.requireUserId();
        ImportMappingTemplate template = mappingTemplate(mappingTemplateId);
        permissionService.requireWorkspacePermission(actorId, template.getWorkspaceId(), "workspace.admin");
        ImportMappingStatusTranslation translation = importMappingStatusTranslationRepository.findByIdAndMappingTemplateId(translationId, template.getId())
                .orElseThrow(() -> notFound("Import mapping status translation not found"));
        importMappingStatusTranslationRepository.delete(translation);
        recordMappingTemplateEvent(template, "import_mapping_status_translation.deleted", actorId);
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
    public ImportJobResponse completeImportJob(UUID importJobId, ImportJobCompleteRequest request) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        long openConflicts = importJobRecordRepository.countByImportJobIdAndConflictStatus(job.getId(), "open");
        String openConflictReason = null;
        if (openConflicts > 0) {
            ImportJobCompleteRequest completeRequest = request == null
                    ? new ImportJobCompleteRequest(null, null, null)
                    : request;
            if (!Boolean.TRUE.equals(completeRequest.acceptOpenConflicts())
                    || !COMPLETE_OPEN_CONFLICTS_CONFIRMATION.equals(completeRequest.openConflictConfirmation())
                    || !hasText(completeRequest.openConflictReason())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Import job has " + openConflicts
                                + " open conflicts; set acceptOpenConflicts to true, openConflictConfirmation to "
                                + COMPLETE_OPEN_CONFLICTS_CONFIRMATION
                                + ", and provide openConflictReason to complete anyway"
                );
            }
            openConflictReason = completeRequest.openConflictReason().trim();
        }
        job.setStatus("completed");
        job.setFinishedAt(OffsetDateTime.now());
        if (openConflicts > 0) {
            job.setOpenConflictCompletionAccepted(true);
            job.setOpenConflictCompletionCount(Math.toIntExact(openConflicts));
            job.setOpenConflictCompletedById(actorId);
            job.setOpenConflictCompletedAt(job.getFinishedAt());
            job.setOpenConflictCompletionReason(openConflictReason);
        } else {
            job.setOpenConflictCompletionAccepted(false);
            job.setOpenConflictCompletionCount(0);
            job.setOpenConflictCompletedById(null);
            job.setOpenConflictCompletedAt(null);
            job.setOpenConflictCompletionReason(null);
        }
        ImportJob saved = importJobRepository.save(job);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("openConflictCount", openConflicts);
        if (openConflicts > 0) {
            payload.put("acceptedOpenConflicts", true)
                    .put("openConflictReason", openConflictReason);
        }
        recordJobEvent(saved, "import_job.completed", actorId, payload);
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
        recordImportJobRecordVersion(saved, "created", actorId);
        recordJobEvent(job, "import_job.record_created", actorId);
        return ImportJobRecordResponse.from(saved);
    }

    @Transactional
    public ImportJobRecordResponse updateRecord(UUID recordId, ImportJobRecordRequest request) {
        ImportJobRecordRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ImportJobRecord record = importJobRecordRepository.findById(recordId)
                .orElseThrow(() -> notFound("Import job record not found"));
        ImportJob job = mutableImportJob(record.getImportJobId());
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        applyRecordRequest(record, updateRequest, false);
        clearConflict(record);
        ImportJobRecord saved = importJobRecordRepository.save(record);
        recordImportJobRecordVersion(saved, "updated", actorId);
        recordJobEvent(job, "import_job.record_updated", actorId);
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
            boolean newRecord = record.getId() == null;
            record.setStatus("pending");
            record.setErrorMessage(null);
            clearConflict(record);
            record.setRawPayload(parsedRecord.rawPayload());
            ImportJobRecord saved = importJobRecordRepository.save(record);
            recordImportJobRecordVersion(saved, newRecord ? "parsed" : "reparsed", actorId);
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
        ImportMappingRules rules = mappingRules(template.getId());
        ImportTransformPreset transformPreset = template.getTransformPresetId() == null
                ? null
                : activeTransformPresetInWorkspace(template.getTransformPresetId(), template.getWorkspaceId());
        JsonNode transformations = transformationConfig(template, transformPreset);
        int limit = normalizeMaterializeLimit(materializeRequest.limit());
        boolean updateExisting = Boolean.TRUE.equals(materializeRequest.updateExisting());
        ImportMaterializationRun materializationRun = startMaterializationRun(
                job,
                template,
                transformPreset,
                project,
                actorId,
                updateExisting,
                transformations,
                rules
        );
        return processMaterialization(job, template, rules, project, materializationRun, updateExisting, transformations, limit, actorId);
    }

    @Transactional
    public ImportMaterializeResponse rerunMaterialization(UUID materializationRunId, ImportMaterializationRerunRequest request) {
        ImportMaterializationRerunRequest rerunRequest = request == null ? new ImportMaterializationRerunRequest(null, null) : request;
        UUID actorId = currentUserService.requireUserId();
        ImportMaterializationRun sourceRun = importMaterializationRunRepository.findById(materializationRunId)
                .orElseThrow(() -> notFound("Import materialization run not found"));
        ImportJob job = mutableImportJob(sourceRun.getImportJobId());
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        if (!job.getWorkspaceId().equals(sourceRun.getWorkspaceId())) {
            throw badRequest("Materialization run does not belong to this import job workspace");
        }
        Project project = activeProjectInWorkspace(sourceRun.getProjectId(), job.getWorkspaceId());
        ImportMappingTemplate template = mappingTemplateFromSnapshot(sourceRun);
        ImportMappingRules rules = mappingRulesFromSnapshot(sourceRun.getMappingRulesSnapshot());
        JsonNode transformations = sourceRun.getTransformationConfigSnapshot() == null
                ? objectMapper.createObjectNode()
                : sourceRun.getTransformationConfigSnapshot().deepCopy();
        boolean updateExisting = rerunRequest.updateExisting() == null
                ? Boolean.TRUE.equals(sourceRun.getUpdateExisting())
                : rerunRequest.updateExisting();
        ImportMaterializationRun rerun = startMaterializationRerun(job, sourceRun, actorId, updateExisting);
        return processMaterialization(
                job,
                template,
                rules,
                project,
                rerun,
                updateExisting,
                transformations,
                normalizeMaterializeLimit(rerunRequest.limit()),
                actorId
        );
    }

    @Transactional(readOnly = true)
    public List<ImportJobRecordResponse> listConflicts(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return importJobRecordRepository.findByImportJobIdAndConflictStatusOrderBySourceTypeAscSourceIdAsc(job.getId(), "open").stream()
                .map(ImportJobRecordResponse::from)
                .toList();
    }

    @Transactional
    public ImportJobRecordResponse resolveConflict(UUID recordId, ImportConflictResolutionRequest request) {
        ImportConflictResolutionRequest resolutionRequest = required(request, "request");
        String resolution = validatedConflictResolution(resolutionRequest.resolution());
        UUID actorId = currentUserService.requireUserId();
        ImportJobRecord record = importJobRecordRepository.findById(recordId)
                .orElseThrow(() -> notFound("Import job record not found"));
        ImportJob job = importJob(record.getImportJobId());
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        applyConflictResolution(record, resolution, actorId);
        ImportJobRecord saved = importJobRecordRepository.save(record);
        recordImportJobRecordVersion(saved, "conflict_resolved", actorId);
        recordConflictResolvedEvent(job, saved, actorId);
        return ImportJobRecordResponse.from(saved);
    }

    @Transactional
    public ImportConflictBulkResolutionResponse resolveConflicts(UUID importJobId, ImportConflictBulkResolutionRequest request) {
        ImportConflictBulkResolutionRequest resolutionRequest = required(request, "request");
        String resolution = validatedConflictResolution(resolutionRequest.resolution());
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        String scope = conflictResolutionScope(resolutionRequest.scope());
        List<ImportJobRecord> records = conflictResolutionRecords(job, resolutionRequest, scope, false);
        if ("filtered".equals(scope)) {
            validateFilteredConflictConfirmation(resolutionRequest, records.size());
        }
        validateConflictResolutionBatchSize(records.size());
        List<ImportJobRecordResponse> responses = new ArrayList<>();
        for (ImportJobRecord record : records) {
            applyConflictResolution(record, resolution, actorId);
            ImportJobRecord saved = importJobRecordRepository.save(record);
            recordImportJobRecordVersion(saved, "conflict_resolved", actorId);
            recordConflictResolvedEvent(job, saved, actorId);
            responses.add(ImportJobRecordResponse.from(saved));
        }
        return new ImportConflictBulkResolutionResponse(job.getId(), resolution, scope, records.size(), responses.size(), responses);
    }

    @Transactional(readOnly = true)
    public ImportConflictBulkResolutionPreviewResponse previewResolveConflicts(UUID importJobId, ImportConflictBulkResolutionRequest request) {
        ImportConflictBulkResolutionRequest resolutionRequest = required(request, "request");
        String resolution = validatedConflictResolution(resolutionRequest.resolution());
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        String scope = conflictResolutionScope(resolutionRequest.scope());
        ConflictResolutionPreview preview = conflictResolutionPreview(job, resolutionRequest, scope);
        List<ImportJobRecordResponse> records = preview.records().stream()
                .map(ImportJobRecordResponse::from)
                .toList();
        return new ImportConflictBulkResolutionPreviewResponse(
                job.getId(),
                resolution,
                scope,
                Math.toIntExact(preview.matched()),
                records.size(),
                preview.page(),
                preview.pageSize(),
                preview.hasMore(),
                MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE,
                records
        );
    }

    @Transactional(readOnly = true)
    public List<ImportConflictResolutionJobResponse> listConflictResolutionJobs(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return importConflictResolutionJobRepository.findByImportJobIdOrderByRequestedAtDesc(job.getId()).stream()
                .map(ImportConflictResolutionJobResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImportConflictResolutionJobResponse> listWorkspaceConflictResolutionJobs(UUID workspaceId, String status) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        String normalizedStatus = normalizedFilter(status);
        List<ImportConflictResolutionJob> jobs = normalizedStatus == null
                ? importConflictResolutionJobRepository.findByWorkspaceIdOrderByRequestedAtDesc(workspaceId)
                : importConflictResolutionJobRepository.findByWorkspaceIdAndStatusOrderByRequestedAtDesc(workspaceId, normalizedStatus);
        return jobs.stream()
                .map(ImportConflictResolutionJobResponse::from)
                .toList();
    }

    @Transactional
    public ImportConflictResolutionJobResponse createConflictResolutionJob(UUID importJobId, ImportConflictBulkResolutionRequest request) {
        ImportConflictBulkResolutionRequest resolutionRequest = required(request, "request");
        String resolution = validatedConflictResolution(resolutionRequest.resolution());
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = mutableImportJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        String scope = conflictResolutionScope(resolutionRequest.scope());
        if (!"filtered".equals(scope)) {
            throw badRequest("Async conflict resolution jobs require scope filtered");
        }
        validateFilteredConflictFilters(resolutionRequest);
        long matched = countFilteredConflicts(job, resolutionRequest);
        if (matched == 0) {
            throw badRequest("No open conflicts match the requested filters");
        }
        validateFilteredConflictConfirmation(resolutionRequest, matched);

        ImportConflictResolutionJob resolutionJob = new ImportConflictResolutionJob();
        resolutionJob.setWorkspaceId(job.getWorkspaceId());
        resolutionJob.setImportJobId(job.getId());
        resolutionJob.setRequestedById(actorId);
        resolutionJob.setResolution(resolution);
        resolutionJob.setScope(scope);
        resolutionJob.setStatus("queued");
        resolutionJob.setStatusFilter(normalizedFilter(resolutionRequest.status()));
        resolutionJob.setConflictStatusFilter("open");
        resolutionJob.setSourceTypeFilter(normalizedFilter(resolutionRequest.sourceType()));
        resolutionJob.setExpectedCount(Math.toIntExact(matched));
        resolutionJob.setMatchedCount(Math.toIntExact(matched));
        resolutionJob.setResolvedCount(0);
        resolutionJob.setFailedCount(0);
        resolutionJob.setConfirmation(resolutionRequest.confirmation());
        resolutionJob.setRequestedAt(OffsetDateTime.now());
        ImportConflictResolutionJob saved = importConflictResolutionJobRepository.save(resolutionJob);
        recordConflictResolutionJobEvent(saved, "import_conflict_resolution_job.queued", actorId);
        return ImportConflictResolutionJobResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ImportConflictResolutionJobResponse getConflictResolutionJob(UUID jobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportConflictResolutionJob resolutionJob = conflictResolutionJob(jobId);
        permissionService.requireWorkspacePermission(actorId, resolutionJob.getWorkspaceId(), "workspace.admin");
        return ImportConflictResolutionJobResponse.from(resolutionJob);
    }

    @Transactional
    public ImportConflictResolutionJobResponse runConflictResolutionJob(UUID jobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportConflictResolutionJob resolutionJob = conflictResolutionJob(jobId);
        permissionService.requireWorkspacePermission(actorId, resolutionJob.getWorkspaceId(), "workspace.admin");
        return runConflictResolutionJob(resolutionJob, actorId);
    }

    @Transactional
    public ImportConflictResolutionJobResponse cancelConflictResolutionJob(UUID jobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportConflictResolutionJob resolutionJob = conflictResolutionJob(jobId);
        permissionService.requireWorkspacePermission(actorId, resolutionJob.getWorkspaceId(), "workspace.admin");
        if (!Set.of("queued", "failed").contains(resolutionJob.getStatus())) {
            throw badRequest("Conflict resolution job must be queued or failed to cancel");
        }
        resolutionJob.setStatus("cancelled");
        resolutionJob.setFinishedAt(OffsetDateTime.now());
        resolutionJob.setErrorMessage("Cancelled by user");
        ImportConflictResolutionJob saved = importConflictResolutionJobRepository.save(resolutionJob);
        recordConflictResolutionJobEvent(saved, "import_conflict_resolution_job.cancelled", actorId);
        return ImportConflictResolutionJobResponse.from(saved);
    }

    @Transactional
    public ImportConflictResolutionJobResponse retryConflictResolutionJob(UUID jobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportConflictResolutionJob resolutionJob = conflictResolutionJob(jobId);
        permissionService.requireWorkspacePermission(actorId, resolutionJob.getWorkspaceId(), "workspace.admin");
        if (!Set.of("failed", "cancelled").contains(resolutionJob.getStatus())) {
            throw badRequest("Conflict resolution job must be failed or cancelled to retry");
        }
        resolutionJob.setStatus("queued");
        resolutionJob.setStartedAt(null);
        resolutionJob.setFinishedAt(null);
        resolutionJob.setErrorMessage(null);
        resolutionJob.setResolvedCount(0);
        resolutionJob.setFailedCount(0);
        resolutionJob.setRequestedAt(OffsetDateTime.now());
        ImportConflictResolutionJob saved = importConflictResolutionJobRepository.save(resolutionJob);
        recordConflictResolutionJobEvent(saved, "import_conflict_resolution_job.retried", actorId);
        return ImportConflictResolutionJobResponse.from(saved);
    }

    @Transactional
    public ImportConflictResolutionWorkerResponse processConflictResolutionJobs(UUID workspaceId, Integer limit) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        return processConflictResolutionJobsInternal(workspaceId, limit, actorId);
    }

    @Transactional
    public ImportConflictResolutionWorkerResponse processConflictResolutionJobsInternal(UUID workspaceId, Integer limit, UUID actorId) {
        if (workspaceId != null) {
            activeWorkspace(workspaceId);
        }
        int normalizedLimit = normalizeConflictResolutionWorkerLimit(limit);
        AutomationWorkerRun run = startImportConflictResolutionWorkerRun(workspaceId, normalizedLimit, actorId);
        try {
            ImportConflictResolutionWorkerResponse response = processQueuedConflictResolutionJobs(workspaceId, normalizedLimit, actorId);
            finishImportConflictResolutionWorkerRun(
                    run,
                    response.processed(),
                    response.completed(),
                    response.failed(),
                    null
            );
            return response;
        } catch (RuntimeException ex) {
            finishImportConflictResolutionWorkerRun(run, 0, 0, 1, ex.getMessage());
            throw ex;
        }
    }

    private ImportConflictResolutionWorkerResponse processQueuedConflictResolutionJobs(UUID workspaceId, int limit, UUID actorId) {
        List<ImportConflictResolutionJob> queuedJobs = workspaceId == null
                ? importConflictResolutionJobRepository.findQueued(PageRequest.of(0, limit))
                : importConflictResolutionJobRepository.findQueuedByWorkspaceId(workspaceId, PageRequest.of(0, limit));
        List<ImportConflictResolutionJobResponse> responses = new ArrayList<>();
        int completed = 0;
        int failed = 0;
        for (ImportConflictResolutionJob queuedJob : queuedJobs) {
            ImportConflictResolutionJobResponse response = runConflictResolutionJob(
                    queuedJob,
                    actorId == null ? queuedJob.getRequestedById() : actorId
            );
            responses.add(response);
            if ("completed".equals(response.status())) {
                completed++;
            } else if ("failed".equals(response.status())) {
                failed++;
            }
        }
        return new ImportConflictResolutionWorkerResponse(workspaceId, responses.size(), completed, failed, responses);
    }

    private AutomationWorkerRun startImportConflictResolutionWorkerRun(UUID workspaceId, Integer limit, UUID actorId) {
        if (workspaceId == null) {
            throw badRequest("workspaceId is required for import conflict resolution worker runs");
        }
        AutomationWorkerRun run = new AutomationWorkerRun();
        run.setWorkspaceId(workspaceId);
        run.setWorkerType(IMPORT_CONFLICT_RESOLUTION_WORKER_TYPE);
        run.setTriggerType(actorId == null ? "scheduled" : "manual");
        run.setStatus("running");
        run.setDryRun(false);
        run.setRequestedLimit(limit);
        run.setMaxAttempts(null);
        run.setProcessedCount(0);
        run.setSuccessCount(0);
        run.setFailureCount(0);
        run.setDeadLetterCount(0);
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("worker", IMPORT_CONFLICT_RESOLUTION_WORKER_TYPE);
        if (actorId != null) {
            metadata.put("actorUserId", actorId.toString());
        }
        run.setMetadata(metadata);
        run.setStartedAt(OffsetDateTime.now());
        AutomationWorkerRun saved = automationWorkerRunRepository.save(run);
        updateImportConflictResolutionWorkerHealth(saved);
        return saved;
    }

    private void finishImportConflictResolutionWorkerRun(
            AutomationWorkerRun run,
            int processed,
            int completed,
            int failed,
            String errorMessage
    ) {
        run.setProcessedCount(processed);
        run.setSuccessCount(completed);
        run.setFailureCount(failed);
        run.setDeadLetterCount(0);
        run.setErrorMessage(truncateText(errorMessage, 4000));
        run.setStatus(hasText(errorMessage) || failed > 0 ? "failed" : "succeeded");
        run.setFinishedAt(OffsetDateTime.now());
        AutomationWorkerRun saved = automationWorkerRunRepository.save(run);
        updateImportConflictResolutionWorkerHealth(saved);
    }

    private void updateImportConflictResolutionWorkerHealth(AutomationWorkerRun run) {
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

    private ImportConflictResolutionJobResponse runConflictResolutionJob(ImportConflictResolutionJob resolutionJob, UUID actorId) {
        if (!"queued".equals(resolutionJob.getStatus())) {
            throw badRequest("Conflict resolution job must be queued to run");
        }
        resolutionJob.setStatus("running");
        resolutionJob.setStartedAt(OffsetDateTime.now());
        resolutionJob.setFinishedAt(null);
        resolutionJob.setErrorMessage(null);
        resolutionJob.setResolvedCount(0);
        resolutionJob.setFailedCount(0);
        ImportConflictResolutionJob running = importConflictResolutionJobRepository.save(resolutionJob);
        recordConflictResolutionJobEvent(running, "import_conflict_resolution_job.running", actorId);
        try {
            ImportJob job = mutableImportJob(resolutionJob.getImportJobId());
            long currentMatched = importJobRecordRepository.countFiltered(
                    job.getId(),
                    resolutionJob.getStatusFilter(),
                    "open",
                    resolutionJob.getSourceTypeFilter()
            );
            if (currentMatched != resolutionJob.getExpectedCount()) {
                throw badRequest("Current filtered open conflict count no longer matches the queued expected count");
            }
            int resolved = resolveFilteredConflictsForJob(job, resolutionJob, actorId);
            resolutionJob.setResolvedCount(resolved);
            resolutionJob.setMatchedCount(Math.toIntExact(currentMatched));
            resolutionJob.setStatus("completed");
            resolutionJob.setFinishedAt(OffsetDateTime.now());
            ImportConflictResolutionJob saved = importConflictResolutionJobRepository.save(resolutionJob);
            recordConflictResolutionJobEvent(saved, "import_conflict_resolution_job.completed", actorId);
            return ImportConflictResolutionJobResponse.from(saved);
        } catch (RuntimeException ex) {
            resolutionJob.setStatus("failed");
            resolutionJob.setFinishedAt(OffsetDateTime.now());
            resolutionJob.setFailedCount(resolutionJob.getExpectedCount() == null ? 0 : resolutionJob.getExpectedCount() - safeCount(resolutionJob.getResolvedCount()));
            resolutionJob.setErrorMessage(ex.getMessage());
            ImportConflictResolutionJob saved = importConflictResolutionJobRepository.save(resolutionJob);
            recordConflictResolutionJobEvent(saved, "import_conflict_resolution_job.failed", actorId);
            return ImportConflictResolutionJobResponse.from(saved);
        }
    }

    private String conflictResolutionScope(String value) {
        String scope = hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "selected";
        if (!Set.of("selected", "filtered").contains(scope)) {
            throw badRequest("scope must be selected or filtered");
        }
        return scope;
    }

    private ConflictResolutionPreview conflictResolutionPreview(
            ImportJob job,
            ImportConflictBulkResolutionRequest request,
            String scope
    ) {
        int page = normalizePreviewPage(request.page());
        int pageSize = normalizePreviewPageSize(request.pageSize());
        if ("filtered".equals(scope)) {
            validateFilteredConflictFilters(request);
            long matched = countFilteredConflicts(job, request);
            List<ImportJobRecord> records = importJobRecordRepository.findFiltered(
                    job.getId(),
                    normalizedFilter(request.status()),
                    "open",
                    normalizedFilter(request.sourceType()),
                    PageRequest.of(page, pageSize)
            );
            return new ConflictResolutionPreview(matched, records, page, pageSize, ((long) (page + 1) * pageSize) < matched);
        }
        List<ImportJobRecord> records = conflictResolutionRecords(job, request, scope, true);
        return new ConflictResolutionPreview(records.size(), records, page, pageSize, false);
    }

    private List<ImportJobRecord> conflictResolutionRecords(
            ImportJob job,
            ImportConflictBulkResolutionRequest request,
            String scope,
            boolean allowEmpty
    ) {
        if ("filtered".equals(scope)) {
            validateFilteredConflictFilters(request);
            long matched = countFilteredConflicts(job, request);
            if (matched > MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE) {
                throw badRequest("Filtered bulk conflict resolution supports at most "
                        + MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE
                        + " open conflicts at a time; narrow the filters or resolve selected preview records in batches");
            }
            List<ImportJobRecord> records = importJobRecordRepository.findFiltered(
                    job.getId(),
                    normalizedFilter(request.status()),
                    "open",
                    normalizedFilter(request.sourceType()),
                    PageRequest.of(0, MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE)
            );
            if (records.isEmpty() && !allowEmpty) {
                throw badRequest("No open conflicts match the requested filters");
            }
            return records;
        }

        List<UUID> recordIds = request.recordIds() == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.recordIds()));
        if (recordIds.isEmpty()) {
            throw badRequest("recordIds is required");
        }
        validateConflictResolutionBatchSize(recordIds.size());
        List<ImportJobRecord> records = importJobRecordRepository.findByIdInAndImportJobIdOrderBySourceTypeAscSourceIdAsc(recordIds, job.getId());
        if (records.size() != recordIds.size()) {
            throw badRequest("All import job records must belong to the import job");
        }
        return records;
    }

    private void validateFilteredConflictConfirmation(ImportConflictBulkResolutionRequest request, long matchedCount) {
        if (!RESOLVE_FILTERED_CONFLICTS_CONFIRMATION.equals(request.confirmation())) {
            throw badRequest("confirmation must equal " + RESOLVE_FILTERED_CONFLICTS_CONFIRMATION);
        }
        if (request.expectedCount() == null || request.expectedCount().longValue() != matchedCount) {
            throw badRequest("expectedCount must match the current filtered open conflict count");
        }
    }

    private void validateFilteredConflictFilters(ImportConflictBulkResolutionRequest request) {
        if (hasText(request.conflictStatus()) && !"open".equals(normalizedFilter(request.conflictStatus()))) {
            throw badRequest("Filtered bulk conflict resolution only supports open conflicts");
        }
    }

    private long countFilteredConflicts(ImportJob job, ImportConflictBulkResolutionRequest request) {
        return importJobRecordRepository.countFiltered(
                job.getId(),
                normalizedFilter(request.status()),
                "open",
                normalizedFilter(request.sourceType())
        );
    }

    private void validateConflictResolutionBatchSize(int recordCount) {
        if (recordCount > MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE) {
            throw badRequest("Bulk conflict resolution supports at most "
                    + MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE
                    + " records at a time");
        }
    }

    private int normalizePreviewPage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw badRequest("page must be zero or greater");
        }
        return page;
    }

    private int normalizePreviewPageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_CONFLICT_PREVIEW_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_CONFLICT_PREVIEW_PAGE_SIZE) {
            throw badRequest("pageSize must be between 1 and " + MAX_CONFLICT_PREVIEW_PAGE_SIZE);
        }
        return pageSize;
    }

    private int normalizeConflictResolutionWorkerLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_CONFLICT_RESOLUTION_WORKER_LIMIT;
        }
        if (limit < 1 || limit > MAX_CONFLICT_RESOLUTION_WORKER_LIMIT) {
            throw badRequest("limit must be between 1 and " + MAX_CONFLICT_RESOLUTION_WORKER_LIMIT);
        }
        return limit;
    }

    private int resolveFilteredConflictsForJob(ImportJob job, ImportConflictResolutionJob resolutionJob, UUID actorId) {
        int resolved = 0;
        while (true) {
            List<ImportJobRecord> records = importJobRecordRepository.findFiltered(
                    job.getId(),
                    resolutionJob.getStatusFilter(),
                    "open",
                    resolutionJob.getSourceTypeFilter(),
                    PageRequest.of(0, MAX_FILTERED_CONFLICT_RESOLUTION_BATCH_SIZE)
            );
            if (records.isEmpty()) {
                return resolved;
            }
            for (ImportJobRecord record : records) {
                applyConflictResolution(record, resolutionJob.getResolution(), actorId);
                ImportJobRecord saved = importJobRecordRepository.save(record);
                recordImportJobRecordVersion(saved, "conflict_resolved", actorId);
                recordConflictResolvedEvent(job, saved, actorId);
                resolved++;
            }
            resolutionJob.setResolvedCount(resolved);
            importConflictResolutionJobRepository.save(resolutionJob);
        }
    }

    @Transactional(readOnly = true)
    public List<ImportJobRecordResponse> listRecords(UUID importJobId, String status, String conflictStatus, String sourceType) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return importJobRecordRepository.findFiltered(job.getId(), normalizedFilter(status), normalizedFilter(conflictStatus), normalizedFilter(sourceType)).stream()
                .map(ImportJobRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImportJobRecordVersionResponse> listRecordVersions(UUID recordId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJobRecord record = importJobRecordRepository.findById(recordId)
                .orElseThrow(() -> notFound("Import job record not found"));
        ImportJob job = importJob(record.getImportJobId());
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return importJobRecordVersionRepository.findByImportJobRecordIdOrderByVersionDesc(record.getId()).stream()
                .map(ImportJobRecordVersionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImportJobRecordVersionDiffResponse> listRecordVersionDiffs(UUID recordId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJobRecord record = importJobRecordRepository.findById(recordId)
                .orElseThrow(() -> notFound("Import job record not found"));
        ImportJob job = importJob(record.getImportJobId());
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return recordVersionDiffs(record);
    }

    @Transactional(readOnly = true)
    public ImportJobVersionDiffResponse listJobVersionDiffs(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        List<ImportJobRecordVersionDiffGroupResponse> groups = importJobRecordRepository
                .findByImportJobIdOrderBySourceTypeAscSourceIdAsc(job.getId()).stream()
                .map(record -> ImportJobRecordVersionDiffGroupResponse.from(record, recordVersionDiffs(record)))
                .filter(group -> !group.diffs().isEmpty())
                .toList();
        int versionCount = groups.stream()
                .mapToInt(group -> group.diffs().size())
                .sum();
        int diffCount = groups.stream()
                .flatMap(group -> group.diffs().stream())
                .mapToInt(diff -> diff.fields().size())
                .sum();
        return new ImportJobVersionDiffResponse(job.getId(), job.getWorkspaceId(), groups.size(), versionCount, diffCount, groups);
    }

    @Transactional(readOnly = true)
    public ImportJobVersionDiffExportResponse exportJobVersionDiffs(UUID importJobId) {
        ImportJobVersionDiffResponse diffs = listJobVersionDiffs(importJobId);
        ImportJob job = importJob(importJobId);
        return new ImportJobVersionDiffExportResponse(OffsetDateTime.now(), response(job), diffs);
    }

    @Transactional
    public ExportJobResponse createJobVersionDiffExportJob(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        ImportJobVersionDiffExportResponse export = new ImportJobVersionDiffExportResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                response(job),
                listJobVersionDiffs(importJobId)
        );
        AttachmentStorageConfig storageConfig = attachmentStorageConfigRepository.findFirstByWorkspaceIdAndActiveTrueAndDefaultConfigTrue(job.getWorkspaceId())
                .orElseThrow(() -> badRequest("Default attachment storage config not found"));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String filename = "import-job-version-diffs-"
                + importJobId
                + "-"
                + now.format(EXPORT_FILENAME_TIME)
                + ".json";
        byte[] content = jsonBytes(export);
        StoredAttachment stored = attachmentStorageService.store(
                storageConfig,
                new AttachmentUpload(filename, "application/json", content, null)
        );
        try {
            Attachment attachment = new Attachment();
            attachment.setWorkspaceId(job.getWorkspaceId());
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
            exportJob.setWorkspaceId(job.getWorkspaceId());
            exportJob.setRequestedById(actorId);
            exportJob.setExportType("import_job_version_diffs");
            exportJob.setStatus("completed");
            exportJob.setFileAttachmentId(savedAttachment.getId());
            exportJob.setStartedAt(now);
            exportJob.setFinishedAt(now);
            ExportJob savedExportJob = exportJobRepository.save(exportJob);
            recordImportJobVersionDiffExportEvent(job, savedExportJob, savedAttachment, actorId);
            return ExportJobResponse.from(savedExportJob, savedAttachment);
        } catch (RuntimeException ex) {
            attachmentStorageService.delete(storageConfig, stored.storageKey());
            throw ex;
        }
    }

    private ImportMaterializeResponse processMaterialization(
            ImportJob job,
            ImportMappingTemplate template,
            ImportMappingRules rules,
            Project project,
            ImportMaterializationRun materializationRun,
            boolean updateExisting,
            JsonNode transformations,
            int limit,
            UUID actorId
    ) {
        List<ImportJobRecord> candidates = importJobRecordRepository
                .findByImportJobIdAndStatusInOrderBySourceTypeAscSourceIdAsc(job.getId(), List.of("pending", "failed", "imported"));
        List<ImportJobRecordResponse> responses = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        int skipped = 0;
        int conflicts = 0;
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
                skipped++;
                conflicts++;
                recordMaterializationConflict(record, materializationRun, "Existing import target would be skipped because updateExisting is false");
                ImportJobRecord saved = importJobRecordRepository.save(record);
                recordImportJobRecordVersion(saved, "conflict_opened", actorId);
                responses.add(ImportJobRecordResponse.from(saved));
                continue;
            }
            String recordChangeType;
            try {
                MaterializedWorkItem workItem = materializeRecord(job, template, rules, project, record, updateExisting, transformations);
                if (workItem.created()) {
                    created++;
                    recordChangeType = "materialized_created";
                } else {
                    updated++;
                    recordChangeType = "materialized_updated";
                }
                clearConflict(record);
                record.setTargetType("work_item");
                record.setTargetId(workItem.response().id());
                record.setStatus("imported");
                record.setErrorMessage(null);
            } catch (ResponseStatusException ex) {
                failed++;
                record.setStatus("failed");
                record.setErrorMessage(ex.getReason());
                recordChangeType = "materialization_failed";
            } catch (RuntimeException ex) {
                failed++;
                record.setStatus("failed");
                record.setErrorMessage(ex.getMessage());
                recordChangeType = "materialization_failed";
            }
            ImportJobRecord saved = importJobRecordRepository.save(record);
            recordImportJobRecordVersion(saved, recordChangeType, actorId);
            responses.add(ImportJobRecordResponse.from(saved));
        }
        finishMaterializationRun(materializationRun, processed, created, updated, failed, skipped, conflicts);
        recordMaterializationEvent(job, materializationRun, actorId);
        return new ImportMaterializeResponse(materializationRun.getId(), job.getId(), template.getId(), project.getId(), processed, created, updated, failed, skipped, conflicts, responses);
    }

    @Transactional(readOnly = true)
    public List<ImportMaterializationRunResponse> listMaterializationRuns(UUID importJobId) {
        UUID actorId = currentUserService.requireUserId();
        ImportJob job = importJob(importJobId);
        permissionService.requireWorkspacePermission(actorId, job.getWorkspaceId(), "workspace.admin");
        return importMaterializationRunRepository.findByImportJobIdOrderByCreatedAtDesc(job.getId()).stream()
                .map(ImportMaterializationRunResponse::from)
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
        if (Boolean.TRUE.equals(request.clearTarget())) {
            record.setTargetType(null);
            record.setTargetId(null);
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
        if (Boolean.TRUE.equals(request.clearTransformPreset())) {
            template.setTransformPresetId(null);
        } else if (create || request.transformPresetId() != null) {
            UUID presetId = request.transformPresetId();
            template.setTransformPresetId(presetId == null ? null : activeTransformPresetInWorkspace(presetId, template.getWorkspaceId()).getId());
        }
        if (create || request.fieldMapping() != null) {
            template.setFieldMapping(toJsonObject(request.fieldMapping()));
        }
        if (create || request.defaults() != null) {
            template.setDefaults(toJsonObject(request.defaults()));
        }
        if (create || request.transformationConfig() != null) {
            JsonNode transformationConfig = toJsonObject(request.transformationConfig());
            validateTransformationConfig(transformationConfig);
            template.setTransformationConfig(transformationConfig);
        }
        if (request.enabled() != null) {
            template.setEnabled(request.enabled());
        } else if (create) {
            template.setEnabled(true);
        }
    }

    private void applyTransformPresetRequest(ImportTransformPreset preset, ImportTransformPresetRequest request, boolean create) {
        if (create || hasText(request.name())) {
            preset.setName(requiredText(request.name(), "name"));
        }
        if (create || request.description() != null) {
            preset.setDescription(hasText(request.description()) ? request.description().trim() : null);
        }
        if (create || request.transformationConfig() != null) {
            JsonNode transformationConfig = toJsonObject(request.transformationConfig());
            validateTransformationConfig(transformationConfig);
            preset.setTransformationConfig(transformationConfig);
        }
        if (request.enabled() != null) {
            preset.setEnabled(request.enabled());
        } else if (create) {
            preset.setEnabled(true);
        }
    }

    private void applyValueLookupRequest(ImportMappingValueLookup lookup, ImportMappingValueLookupRequest request, boolean create) {
        if (create || hasText(request.sourceField())) {
            lookup.setSourceField(requiredText(request.sourceField(), "sourceField"));
        }
        if (create || request.sourceValue() != null) {
            lookup.setSourceValue(requiredText(request.sourceValue(), "sourceValue"));
        }
        if (create || hasText(request.targetField())) {
            lookup.setTargetField(requiredText(request.targetField(), "targetField"));
        }
        if (create || request.targetValue() != null) {
            lookup.setTargetValue(objectMapper.valueToTree(required(request.targetValue(), "targetValue")));
        }
        if (request.sortOrder() != null) {
            lookup.setSortOrder(request.sortOrder());
        } else if (create) {
            lookup.setSortOrder(0);
        }
        if (request.enabled() != null) {
            lookup.setEnabled(request.enabled());
        } else if (create) {
            lookup.setEnabled(true);
        }
    }

    private void applyTypeTranslationRequest(ImportMappingTypeTranslation translation, ImportMappingTypeTranslationRequest request, boolean create) {
        if (create || hasText(request.sourceTypeKey())) {
            translation.setSourceTypeKey(requiredText(request.sourceTypeKey(), "sourceTypeKey"));
        }
        if (create || hasText(request.targetTypeKey())) {
            translation.setTargetTypeKey(requiredText(request.targetTypeKey(), "targetTypeKey"));
        }
        if (request.enabled() != null) {
            translation.setEnabled(request.enabled());
        } else if (create) {
            translation.setEnabled(true);
        }
    }

    private void applyStatusTranslationRequest(ImportMappingStatusTranslation translation, ImportMappingStatusTranslationRequest request, boolean create) {
        if (create || hasText(request.sourceStatusKey())) {
            translation.setSourceStatusKey(requiredText(request.sourceStatusKey(), "sourceStatusKey"));
        }
        if (create || hasText(request.targetStatusKey())) {
            translation.setTargetStatusKey(requiredText(request.targetStatusKey(), "targetStatusKey"));
        }
        if (request.enabled() != null) {
            translation.setEnabled(request.enabled());
        } else if (create) {
            translation.setEnabled(true);
        }
    }

    private String validatedConflictResolution(String value) {
        String resolution = requiredText(value, "resolution").toLowerCase(Locale.ROOT);
        if (!Set.of("skip", "update_existing", "create_new").contains(resolution)) {
            throw badRequest("resolution must be skip, update_existing, or create_new");
        }
        return resolution;
    }

    private void applyConflictResolution(ImportJobRecord record, String resolution, UUID actorId) {
        if (!"open".equals(record.getConflictStatus())) {
            throw badRequest("Import job record does not have an open conflict");
        }
        record.setConflictStatus("resolved");
        record.setConflictResolution(resolution);
        record.setConflictResolvedAt(OffsetDateTime.now());
        record.setConflictResolvedById(actorId);
        if ("skip".equals(resolution)) {
            record.setStatus("skipped");
            record.setErrorMessage(null);
        } else if ("update_existing".equals(resolution)) {
            record.setStatus("pending");
            record.setErrorMessage(null);
        } else {
            record.setStatus("pending");
            record.setTargetType(null);
            record.setTargetId(null);
            record.setErrorMessage(null);
        }
    }

    private MaterializedWorkItem materializeRecord(
            ImportJob job,
            ImportMappingTemplate template,
            ImportMappingRules rules,
            Project project,
            ImportJobRecord record,
            boolean updateExisting,
            JsonNode transformations
    ) {
        JsonNode rawPayload = record.getRawPayload() == null ? objectMapper.createObjectNode() : record.getRawPayload();
        JsonNode mapping = template.getFieldMapping() == null ? objectMapper.createObjectNode() : template.getFieldMapping();
        JsonNode defaults = template.getDefaults() == null ? objectMapper.createObjectNode() : template.getDefaults();
        if (record.getTargetId() != null && updateExisting) {
            WorkItemUpdateRequest updateRequest = new WorkItemUpdateRequest(
                    null,
                    translateType(textValue(rawPayload, mapping, defaults, transformations, rules, "typeKey", null), rules, null),
                    null,
                    null,
                    null,
                    textValue(rawPayload, mapping, defaults, transformations, rules, "priorityKey", null),
                    null,
                    null,
                    textValue(rawPayload, mapping, defaults, transformations, rules, "title", null),
                    textValue(rawPayload, mapping, defaults, transformations, rules, "descriptionMarkdown", null),
                    null,
                    textValue(rawPayload, mapping, defaults, transformations, rules, "visibility", null),
                    null,
                    null,
                    null,
                    null,
                    null,
                    objectValue(rawPayload, mapping, defaults, "customFields")
            );
            return new MaterializedWorkItem(workItemService.update(record.getTargetId(), updateRequest), false);
        }
        String typeKey = translateType(firstText(
                textValue(rawPayload, mapping, defaults, transformations, rules, "typeKey", null),
                template.getWorkItemTypeKey(),
                defaultText(defaults, "typeKey")
        ), rules, template.getWorkItemTypeKey());
        String statusKey = translateStatus(firstText(
                textValue(rawPayload, mapping, defaults, transformations, rules, "statusKey", null),
                template.getStatusKey(),
                defaultText(defaults, "statusKey")
        ), rules, template.getStatusKey());
        WorkItemCreateRequest createRequest = new WorkItemCreateRequest(
                null,
                typeKey,
                null,
                null,
                statusKey,
                null,
                textValue(rawPayload, mapping, defaults, transformations, rules, "priorityKey", null),
                null,
                null,
                null,
                requiredText(textValue(rawPayload, mapping, defaults, transformations, rules, "title", null), "mapped title"),
                textValue(rawPayload, mapping, defaults, transformations, rules, "descriptionMarkdown", "Imported from " + job.getProvider() + " " + record.getSourceId()),
                null,
                textValue(rawPayload, mapping, defaults, transformations, rules, "visibility", null),
                null,
                null,
                null,
                null,
                null,
                objectValue(rawPayload, mapping, defaults, "customFields")
        );
        return new MaterializedWorkItem(workItemService.create(project.getId(), createRequest), true);
    }

    private String textValue(
            JsonNode rawPayload,
            JsonNode mapping,
            JsonNode defaults,
            JsonNode transformations,
            ImportMappingRules rules,
            String targetField,
            String fallback
    ) {
        JsonNode lookupValue = lookupValue(rawPayload, rules.lookups(), targetField);
        if (hasTextNode(lookupValue)) {
            return transformText(lookupValue.isValueNode() ? lookupValue.asText().trim() : lookupValue.toString(), transformations, targetField);
        }
        JsonNode mapped = mappedNode(rawPayload, mapping, targetField);
        if (hasTextNode(mapped)) {
            return transformText(mapped.isValueNode() ? mapped.asText().trim() : mapped.toString(), transformations, targetField);
        }
        String defaultValue = defaultText(defaults, targetField);
        return transformText(hasText(defaultValue) ? defaultValue : fallback, transformations, targetField);
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

    private JsonNode lookupValue(JsonNode rawPayload, List<ImportMappingValueLookup> lookups, String targetField) {
        for (ImportMappingValueLookup lookup : lookups) {
            if (!targetField.equals(lookup.getTargetField())) {
                continue;
            }
            JsonNode source = nodeAtPath(rawPayload, lookup.getSourceField());
            if (!hasTextNode(source)) {
                continue;
            }
            String sourceText = source.isValueNode() ? source.asText().trim() : source.toString();
            if (sourceText.equalsIgnoreCase(lookup.getSourceValue().trim())) {
                return lookup.getTargetValue();
            }
        }
        return null;
    }

    private String transformText(String value, JsonNode transformations, String targetField) {
        if (!hasText(value) || transformations == null || !transformations.has(targetField)) {
            return value;
        }
        JsonNode functions = transformations.get(targetField);
        String transformed = value;
        if (functions.isTextual() || isTransformStep(functions)) {
            return applyTransform(transformed, functions);
        }
        if (functions.isObject() && functions.has("pipeline")) {
            functions = functions.get("pipeline");
        }
        if (functions.isArray()) {
            for (JsonNode function : functions) {
                transformed = applyTransform(transformed, function);
            }
        }
        return transformed;
    }

    private void validateTransformationConfig(JsonNode transformations) {
        if (transformations == null || transformations.isNull()) {
            return;
        }
        if (!transformations.isObject()) {
            throw badRequest("transformationConfig must be a JSON object");
        }
        transformations.fields().forEachRemaining(entry -> validateTransformSteps(entry.getKey(), entry.getValue()));
    }

    private void validateTransformSteps(String targetField, JsonNode steps) {
        if (steps == null || steps.isNull()) {
            return;
        }
        if (steps.isTextual() || isTransformStep(steps)) {
            validateTransformStep(targetField, steps);
            return;
        }
        JsonNode pipeline = steps.isObject() && steps.has("pipeline") ? steps.get("pipeline") : steps;
        if (!pipeline.isArray()) {
            throw badRequest("transformationConfig." + targetField + " must be a transform step or pipeline array");
        }
        for (JsonNode step : pipeline) {
            validateTransformStep(targetField, step);
        }
    }

    private void validateTransformStep(String targetField, JsonNode step) {
        String functionName = step != null && step.isTextual()
                ? step.asText()
                : firstText(text(step, "function"), text(step, "name"), text(step, "type"));
        if (!hasText(functionName)) {
            throw badRequest("transformationConfig." + targetField + " has a transform step without a function");
        }
        String normalized = functionName.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TRANSFORM_FUNCTIONS.contains(normalized)) {
            throw badRequest("Unsupported transform function: " + functionName);
        }
    }

    private boolean isTransformStep(JsonNode node) {
        return node != null
                && node.isObject()
                && (node.hasNonNull("function") || node.hasNonNull("name") || node.hasNonNull("type"));
    }

    private String applyTransform(String value, JsonNode step) {
        if (step == null || step.isNull()) {
            return value;
        }
        if (step.isTextual()) {
            return applyTransform(value, step.asText(), objectMapper.createObjectNode());
        }
        if (!step.isObject()) {
            return value;
        }
        String functionName = firstText(text(step, "function"), text(step, "name"), text(step, "type"));
        JsonNode args = step.has("args") && step.get("args").isObject() ? step.get("args") : step;
        return applyTransform(value, functionName, args);
    }

    private String applyTransform(String value, String functionName, JsonNode args) {
        if (!hasText(functionName)) {
            return value;
        }
        return switch (functionName.trim().toLowerCase(Locale.ROOT)) {
            case "trim" -> value.trim();
            case "lower", "lowercase" -> value.toLowerCase(Locale.ROOT);
            case "upper", "uppercase" -> value.toUpperCase(Locale.ROOT);
            case "collapse_whitespace" -> value.trim().replaceAll("\\s+", " ");
            case "replace" -> replace(value, args);
            case "prefix", "prepend" -> textArg(args, "value", "") + value;
            case "suffix", "append" -> value + textArg(args, "value", "");
            case "truncate" -> truncate(value, intArg(args, "maxLength", intArg(args, "length", value.length())));
            case "substring" -> substring(value, intArg(args, "start", 0), intArg(args, "end", value.length()));
            default -> throw badRequest("Unsupported transform function: " + functionName);
        };
    }

    private String replace(String value, JsonNode args) {
        String replacement = textArg(args, "replacement", "");
        String target = firstText(text(args, "target"), text(args, "pattern"));
        if (!hasText(target)) {
            return value;
        }
        if (Boolean.TRUE.equals(booleanArg(args, "regex"))) {
            try {
                return value.replaceAll(target, replacement);
            } catch (PatternSyntaxException ex) {
                throw badRequest("replace regex pattern is invalid");
            }
        }
        return value.replace(target, replacement);
    }

    private String truncate(String value, int maxLength) {
        if (maxLength < 0) {
            throw badRequest("truncate maxLength must be zero or greater");
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String substring(String value, int start, int end) {
        if (start < 0 || end < start) {
            throw badRequest("substring start/end arguments are invalid");
        }
        int safeStart = Math.min(start, value.length());
        int safeEnd = Math.min(end, value.length());
        return value.substring(safeStart, safeEnd);
    }

    private String text(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) ? node.get(fieldName).asText() : null;
    }

    private String textArg(JsonNode node, String fieldName, String fallback) {
        String value = text(node, fieldName);
        return value == null ? fallback : value;
    }

    private int intArg(JsonNode node, String fieldName, int fallback) {
        return node != null && node.hasNonNull(fieldName) && node.get(fieldName).canConvertToInt()
                ? node.get(fieldName).asInt()
                : fallback;
    }

    private Boolean booleanArg(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) && node.get(fieldName).asBoolean(false);
    }

    private String translateType(String sourceTypeKey, ImportMappingRules rules, String fallback) {
        if (!hasText(sourceTypeKey)) {
            return fallback;
        }
        for (ImportMappingTypeTranslation translation : rules.typeTranslations()) {
            if (sourceTypeKey.trim().equalsIgnoreCase(translation.getSourceTypeKey())) {
                return translation.getTargetTypeKey();
            }
        }
        return sourceTypeKey;
    }

    private String translateStatus(String sourceStatusKey, ImportMappingRules rules, String fallback) {
        if (!hasText(sourceStatusKey)) {
            return fallback;
        }
        for (ImportMappingStatusTranslation translation : rules.statusTranslations()) {
            if (sourceStatusKey.trim().equalsIgnoreCase(translation.getSourceStatusKey())) {
                return translation.getTargetStatusKey();
            }
        }
        return sourceStatusKey;
    }

    private ImportMappingRules mappingRules(UUID mappingTemplateId) {
        return new ImportMappingRules(
                importMappingValueLookupRepository.findByMappingTemplateIdAndEnabledTrueOrderBySortOrderAscSourceFieldAsc(mappingTemplateId),
                importMappingTypeTranslationRepository.findByMappingTemplateIdAndEnabledTrueOrderBySourceTypeKeyAsc(mappingTemplateId),
                importMappingStatusTranslationRepository.findByMappingTemplateIdAndEnabledTrueOrderBySourceStatusKeyAsc(mappingTemplateId)
        );
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

    private ImportConflictResolutionJob conflictResolutionJob(UUID jobId) {
        return importConflictResolutionJobRepository.findById(jobId)
                .orElseThrow(() -> notFound("Import conflict resolution job not found"));
    }

    private ImportMappingTemplate mappingTemplate(UUID mappingTemplateId) {
        return importMappingTemplateRepository.findById(mappingTemplateId).orElseThrow(() -> notFound("Import mapping template not found"));
    }

    private ImportTransformPreset transformPreset(UUID presetId) {
        return importTransformPresetRepository.findById(presetId).orElseThrow(() -> notFound("Import transform preset not found"));
    }

    private ImportTransformPresetVersion transformPresetVersion(UUID presetId, UUID versionId) {
        return importTransformPresetVersionRepository.findByIdAndPresetId(versionId, presetId)
                .orElseThrow(() -> notFound("Import transform preset version not found"));
    }

    private ImportTransformPreset createTransformPresetClone(
            ImportTransformPreset sourcePreset,
            ImportTransformPresetVersion version,
            String requestedName,
            String requestedDescription,
            Boolean requestedEnabled
    ) {
        ImportTransformPreset clone = new ImportTransformPreset();
        clone.setWorkspaceId(sourcePreset.getWorkspaceId());
        clone.setName(uniquePresetName(
                sourcePreset.getWorkspaceId(),
                hasText(requestedName) ? requestedName.trim() : version.getName() + " copy"
        ));
        clone.setDescription(hasText(requestedDescription) ? requestedDescription.trim() : version.getDescription());
        clone.setTransformationConfig(version.getTransformationConfig() == null ? objectMapper.createObjectNode() : version.getTransformationConfig().deepCopy());
        clone.setEnabled(requestedEnabled == null ? Boolean.TRUE.equals(version.getEnabled()) : requestedEnabled);
        clone.setVersion(1);
        return importTransformPresetRepository.saveAndFlush(clone);
    }

    private List<ImportMappingTemplate> selectedRetargetTemplates(UUID workspaceId, List<UUID> mappingTemplateIds) {
        List<UUID> selectedIds = mappingTemplateIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(mappingTemplateIds));
        if (selectedIds.isEmpty()) {
            throw badRequest("mappingTemplateIds is required");
        }
        List<ImportMappingTemplate> templates = importMappingTemplateRepository.findByIdInAndWorkspaceIdOrderByNameAsc(selectedIds, workspaceId);
        if (templates.size() != selectedIds.size()) {
            throw badRequest("All mapping templates must belong to the source preset workspace");
        }
        return templates;
    }

    private List<ImportTransformPresetRetargetTemplateResponse> retargetTemplateResponses(
            List<ImportMappingTemplate> templates,
            UUID newTransformPresetId
    ) {
        return templates.stream()
                .map(template -> ImportTransformPresetRetargetTemplateResponse.from(
                        template,
                        newTransformPresetId,
                        true,
                        "Template will use the cloned transform preset"
                ))
                .toList();
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

    private ImportMaterializationRun startMaterializationRun(
            ImportJob job,
            ImportMappingTemplate template,
            ImportTransformPreset transformPreset,
            Project project,
            UUID actorId,
            boolean updateExisting,
            JsonNode transformations,
            ImportMappingRules rules
    ) {
        ImportMaterializationRun run = new ImportMaterializationRun();
        run.setWorkspaceId(job.getWorkspaceId());
        run.setImportJobId(job.getId());
        run.setMappingTemplateId(template.getId());
        run.setTransformPresetId(transformPreset == null ? null : transformPreset.getId());
        run.setTransformPresetVersion(transformPreset == null ? null : transformPreset.getVersion());
        run.setProjectId(project.getId());
        run.setRequestedById(actorId);
        run.setUpdateExisting(updateExisting);
        run.setMappingTemplateSnapshot(mappingTemplateSnapshot(template));
        run.setTransformPresetSnapshot(transformPresetSnapshot(transformPreset));
        run.setTransformationConfigSnapshot(transformations == null ? objectMapper.createObjectNode() : transformations.deepCopy());
        run.setMappingRulesSnapshot(mappingRulesSnapshot(rules));
        run.setStatus("running");
        run.setRecordsProcessed(0);
        run.setRecordsCreated(0);
        run.setRecordsUpdated(0);
        run.setRecordsFailed(0);
        run.setRecordsSkipped(0);
        run.setRecordsConflicted(0);
        return importMaterializationRunRepository.saveAndFlush(run);
    }

    private ImportMaterializationRun startMaterializationRerun(
            ImportJob job,
            ImportMaterializationRun sourceRun,
            UUID actorId,
            boolean updateExisting
    ) {
        ImportMaterializationRun run = new ImportMaterializationRun();
        run.setWorkspaceId(job.getWorkspaceId());
        run.setImportJobId(job.getId());
        run.setMappingTemplateId(sourceRun.getMappingTemplateId());
        run.setTransformPresetId(sourceRun.getTransformPresetId());
        run.setTransformPresetVersion(sourceRun.getTransformPresetVersion());
        run.setProjectId(sourceRun.getProjectId());
        run.setRequestedById(actorId);
        run.setUpdateExisting(updateExisting);
        run.setMappingTemplateSnapshot(sourceRun.getMappingTemplateSnapshot() == null ? objectMapper.createObjectNode() : sourceRun.getMappingTemplateSnapshot().deepCopy());
        run.setTransformPresetSnapshot(sourceRun.getTransformPresetSnapshot() == null ? null : sourceRun.getTransformPresetSnapshot().deepCopy());
        run.setTransformationConfigSnapshot(sourceRun.getTransformationConfigSnapshot() == null ? objectMapper.createObjectNode() : sourceRun.getTransformationConfigSnapshot().deepCopy());
        run.setMappingRulesSnapshot(sourceRun.getMappingRulesSnapshot() == null ? objectMapper.createObjectNode() : sourceRun.getMappingRulesSnapshot().deepCopy());
        run.setStatus("running");
        run.setRecordsProcessed(0);
        run.setRecordsCreated(0);
        run.setRecordsUpdated(0);
        run.setRecordsFailed(0);
        run.setRecordsSkipped(0);
        run.setRecordsConflicted(0);
        return importMaterializationRunRepository.saveAndFlush(run);
    }

    private void finishMaterializationRun(
            ImportMaterializationRun run,
            int processed,
            int created,
            int updated,
            int failed,
            int skipped,
            int conflicts
    ) {
        run.setRecordsProcessed(processed);
        run.setRecordsCreated(created);
        run.setRecordsUpdated(updated);
        run.setRecordsFailed(failed);
        run.setRecordsSkipped(skipped);
        run.setRecordsConflicted(conflicts);
        run.setStatus(failed > 0 ? "completed_with_failures" : "completed");
        run.setFinishedAt(OffsetDateTime.now());
        importMaterializationRunRepository.save(run);
    }

    private ObjectNode mappingTemplateSnapshot(ImportMappingTemplate template) {
        ObjectNode snapshot = objectMapper.createObjectNode()
                .put("id", template.getId().toString())
                .put("workspaceId", template.getWorkspaceId().toString())
                .put("name", template.getName())
                .put("provider", template.getProvider())
                .put("targetType", template.getTargetType())
                .put("enabled", Boolean.TRUE.equals(template.getEnabled()));
        if (template.getProjectId() != null) {
            snapshot.put("projectId", template.getProjectId().toString());
        }
        if (hasText(template.getSourceType())) {
            snapshot.put("sourceType", template.getSourceType());
        }
        if (hasText(template.getWorkItemTypeKey())) {
            snapshot.put("workItemTypeKey", template.getWorkItemTypeKey());
        }
        if (hasText(template.getStatusKey())) {
            snapshot.put("statusKey", template.getStatusKey());
        }
        if (template.getTransformPresetId() != null) {
            snapshot.put("transformPresetId", template.getTransformPresetId().toString());
        }
        snapshot.set("fieldMapping", template.getFieldMapping() == null ? objectMapper.createObjectNode() : template.getFieldMapping().deepCopy());
        snapshot.set("defaults", template.getDefaults() == null ? objectMapper.createObjectNode() : template.getDefaults().deepCopy());
        snapshot.set("transformationConfig", template.getTransformationConfig() == null ? objectMapper.createObjectNode() : template.getTransformationConfig().deepCopy());
        return snapshot;
    }

    private ObjectNode mappingRulesSnapshot(ImportMappingRules rules) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        ArrayNode lookups = objectMapper.createArrayNode();
        for (ImportMappingValueLookup lookup : rules.lookups()) {
            ObjectNode row = objectMapper.createObjectNode()
                    .put("sourceField", lookup.getSourceField())
                    .put("sourceValue", lookup.getSourceValue())
                    .put("targetField", lookup.getTargetField())
                    .put("sortOrder", lookup.getSortOrder() == null ? 0 : lookup.getSortOrder());
            row.set("targetValue", lookup.getTargetValue() == null ? objectMapper.nullNode() : lookup.getTargetValue().deepCopy());
            lookups.add(row);
        }
        ArrayNode typeTranslations = objectMapper.createArrayNode();
        for (ImportMappingTypeTranslation translation : rules.typeTranslations()) {
            typeTranslations.add(objectMapper.createObjectNode()
                    .put("sourceTypeKey", translation.getSourceTypeKey())
                    .put("targetTypeKey", translation.getTargetTypeKey()));
        }
        ArrayNode statusTranslations = objectMapper.createArrayNode();
        for (ImportMappingStatusTranslation translation : rules.statusTranslations()) {
            statusTranslations.add(objectMapper.createObjectNode()
                    .put("sourceStatusKey", translation.getSourceStatusKey())
                    .put("targetStatusKey", translation.getTargetStatusKey()));
        }
        snapshot.set("valueLookups", lookups);
        snapshot.set("typeTranslations", typeTranslations);
        snapshot.set("statusTranslations", statusTranslations);
        return snapshot;
    }

    private ImportMappingTemplate mappingTemplateFromSnapshot(ImportMaterializationRun run) {
        JsonNode snapshot = run.getMappingTemplateSnapshot();
        if (snapshot == null || !snapshot.isObject()) {
            throw badRequest("Materialization run has no mapping template snapshot");
        }
        ImportMappingTemplate template = new ImportMappingTemplate();
        template.setId(run.getMappingTemplateId());
        template.setWorkspaceId(run.getWorkspaceId());
        template.setProjectId(run.getProjectId());
        template.setName(text(snapshot, "name"));
        template.setProvider(text(snapshot, "provider"));
        template.setSourceType(text(snapshot, "sourceType"));
        template.setTargetType(firstText(text(snapshot, "targetType"), "work_item"));
        template.setWorkItemTypeKey(text(snapshot, "workItemTypeKey"));
        template.setStatusKey(text(snapshot, "statusKey"));
        template.setTransformPresetId(run.getTransformPresetId());
        template.setFieldMapping(snapshot.has("fieldMapping") && snapshot.get("fieldMapping").isObject() ? snapshot.get("fieldMapping").deepCopy() : objectMapper.createObjectNode());
        template.setDefaults(snapshot.has("defaults") && snapshot.get("defaults").isObject() ? snapshot.get("defaults").deepCopy() : objectMapper.createObjectNode());
        template.setTransformationConfig(snapshot.has("transformationConfig") && snapshot.get("transformationConfig").isObject() ? snapshot.get("transformationConfig").deepCopy() : objectMapper.createObjectNode());
        template.setEnabled(true);
        return template;
    }

    private ImportMappingRules mappingRulesFromSnapshot(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject()) {
            return new ImportMappingRules(List.of(), List.of(), List.of());
        }
        List<ImportMappingValueLookup> lookups = new ArrayList<>();
        JsonNode lookupRows = snapshot.get("valueLookups");
        if (lookupRows != null && lookupRows.isArray()) {
            for (JsonNode row : lookupRows) {
                ImportMappingValueLookup lookup = new ImportMappingValueLookup();
                lookup.setSourceField(text(row, "sourceField"));
                lookup.setSourceValue(text(row, "sourceValue"));
                lookup.setTargetField(text(row, "targetField"));
                lookup.setTargetValue(row.has("targetValue") ? row.get("targetValue").deepCopy() : objectMapper.nullNode());
                lookup.setSortOrder(row.hasNonNull("sortOrder") ? row.get("sortOrder").asInt() : 0);
                lookup.setEnabled(true);
                lookups.add(lookup);
            }
        }
        List<ImportMappingTypeTranslation> typeTranslations = new ArrayList<>();
        JsonNode typeRows = snapshot.get("typeTranslations");
        if (typeRows != null && typeRows.isArray()) {
            for (JsonNode row : typeRows) {
                ImportMappingTypeTranslation translation = new ImportMappingTypeTranslation();
                translation.setSourceTypeKey(text(row, "sourceTypeKey"));
                translation.setTargetTypeKey(text(row, "targetTypeKey"));
                translation.setEnabled(true);
                typeTranslations.add(translation);
            }
        }
        List<ImportMappingStatusTranslation> statusTranslations = new ArrayList<>();
        JsonNode statusRows = snapshot.get("statusTranslations");
        if (statusRows != null && statusRows.isArray()) {
            for (JsonNode row : statusRows) {
                ImportMappingStatusTranslation translation = new ImportMappingStatusTranslation();
                translation.setSourceStatusKey(text(row, "sourceStatusKey"));
                translation.setTargetStatusKey(text(row, "targetStatusKey"));
                translation.setEnabled(true);
                statusTranslations.add(translation);
            }
        }
        return new ImportMappingRules(lookups, typeTranslations, statusTranslations);
    }

    private ObjectNode transformPresetSnapshot(ImportTransformPreset preset) {
        if (preset == null) {
            return null;
        }
        ObjectNode snapshot = objectMapper.createObjectNode()
                .put("id", preset.getId().toString())
                .put("workspaceId", preset.getWorkspaceId().toString())
                .put("name", preset.getName())
                .put("enabled", Boolean.TRUE.equals(preset.getEnabled()))
                .put("version", preset.getVersion() == null ? 1 : preset.getVersion());
        if (hasText(preset.getDescription())) {
            snapshot.put("description", preset.getDescription());
        }
        snapshot.set("transformationConfig", preset.getTransformationConfig() == null ? objectMapper.createObjectNode() : preset.getTransformationConfig().deepCopy());
        return snapshot;
    }

    private ImportTransformPreset activeTransformPresetInWorkspace(UUID presetId, UUID workspaceId) {
        ImportTransformPreset preset = importTransformPresetRepository.findByIdAndWorkspaceId(presetId, workspaceId)
                .orElseThrow(() -> badRequest("Transform preset not found in this workspace"));
        if (!Boolean.TRUE.equals(preset.getEnabled())) {
            throw badRequest("Transform preset is disabled");
        }
        return preset;
    }

    private JsonNode transformationConfig(ImportMappingTemplate template, ImportTransformPreset preset) {
        ObjectNode merged = objectMapper.createObjectNode();
        if (preset != null) {
            mergeTransformConfig(merged, preset.getTransformationConfig());
        }
        mergeTransformConfig(merged, template.getTransformationConfig());
        return merged;
    }

    private void mergeTransformConfig(ObjectNode target, JsonNode source) {
        if (source == null || source.isNull()) {
            return;
        }
        if (!source.isObject()) {
            throw badRequest("transformationConfig must be a JSON object");
        }
        source.fields().forEachRemaining(entry -> target.set(entry.getKey(), entry.getValue()));
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

    private ArrayNode textArray(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private ImportSampleDefinition importSample(String sampleKey) {
        String normalized = requiredText(sampleKey, "sampleKey").toLowerCase(Locale.ROOT);
        return IMPORT_SAMPLES.stream()
                .filter(sample -> sample.key().equals(normalized))
                .findFirst()
                .orElseThrow(() -> notFound("Import sample not found"));
    }

    private void requireSampleJobsEnabled() {
        if (sampleJobsEnabled || isLocalSampleRuntime()) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Import sample demo endpoints are disabled for this deployment"
        );
    }

    private boolean isLocalSampleRuntime() {
        return !environment.acceptsProfiles(Profiles.of("prod", "production", "staging", "hosted"));
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String truncateText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void recordMaterializationConflict(ImportJobRecord record, ImportMaterializationRun run, String reason) {
        if (!"open".equals(record.getConflictStatus())) {
            record.setConflictDetectedAt(OffsetDateTime.now());
        }
        record.setStatus("conflict");
        record.setErrorMessage(reason);
        record.setConflictStatus("open");
        record.setConflictReason(reason);
        record.setConflictResolution(null);
        record.setConflictResolvedAt(null);
        record.setConflictResolvedById(null);
        record.setConflictMaterializationRunId(run.getId());
    }

    private void clearConflict(ImportJobRecord record) {
        record.setConflictStatus(null);
        record.setConflictReason(null);
        record.setConflictDetectedAt(null);
        record.setConflictResolution(null);
        record.setConflictResolvedAt(null);
        record.setConflictResolvedById(null);
        record.setConflictMaterializationRunId(null);
    }

    private String uniquePresetName(UUID workspaceId, String requestedName) {
        String base = requiredText(requestedName, "name");
        Set<String> existing = importTransformPresetRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(ImportTransformPreset::getName)
                .collect(java.util.stream.Collectors.toSet());
        if (!existing.contains(base)) {
            return base;
        }
        int suffix = 2;
        while (existing.contains(base + " " + suffix)) {
            suffix++;
        }
        return base + " " + suffix;
    }

    private void recordJobEvent(ImportJob job, String eventType, UUID actorId) {
        recordJobEvent(job, eventType, actorId, null);
    }

    private void recordJobEvent(ImportJob job, String eventType, UUID actorId, ObjectNode additionalPayload) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("importJobId", job.getId().toString())
                .put("provider", job.getProvider())
                .put("status", job.getStatus())
                .put("actorUserId", actorId.toString());
        if (additionalPayload != null) {
            additionalPayload.fields().forEachRemaining(entry -> payload.set(entry.getKey(), entry.getValue()));
        }
        domainEventService.record(job.getWorkspaceId(), "import_job", job.getId(), eventType, payload);
    }

    private void recordSampleJobEvent(
            ImportJob job,
            ImportSampleDefinition sample,
            UUID actorId,
            ImportMappingTemplateResponse mappingTemplate
    ) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("sampleKey", sample.key())
                .put("sampleProvider", sample.provider())
                .put("sourceType", sample.sourceType());
        if (mappingTemplate != null) {
            payload.put("mappingTemplateId", mappingTemplate.id().toString());
        }
        recordJobEvent(job, "import_job.sample_created", actorId, payload);
    }

    private void recordMaterializationEvent(ImportJob job, ImportMaterializationRun run, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("importJobId", job.getId().toString())
                .put("materializationRunId", run.getId().toString())
                .put("provider", job.getProvider())
                .put("status", run.getStatus())
                .put("recordsProcessed", run.getRecordsProcessed())
                .put("recordsCreated", run.getRecordsCreated())
                .put("recordsUpdated", run.getRecordsUpdated())
                .put("recordsFailed", run.getRecordsFailed())
                .put("recordsSkipped", run.getRecordsSkipped())
                .put("recordsConflicted", run.getRecordsConflicted())
                .put("actorUserId", actorId.toString());
        if (run.getMappingTemplateId() != null) {
            payload.put("mappingTemplateId", run.getMappingTemplateId().toString());
        }
        if (run.getTransformPresetId() != null) {
            payload.put("transformPresetId", run.getTransformPresetId().toString());
        }
        if (run.getTransformPresetVersion() != null) {
            payload.put("transformPresetVersion", run.getTransformPresetVersion());
        }
        domainEventService.record(job.getWorkspaceId(), "import_job", job.getId(), "import_job.materialized", payload);
    }

    private void recordConflictResolvedEvent(ImportJob job, ImportJobRecord record, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("importJobId", job.getId().toString())
                .put("importJobRecordId", record.getId().toString())
                .put("sourceType", record.getSourceType())
                .put("sourceId", record.getSourceId())
                .put("resolution", record.getConflictResolution())
                .put("actorUserId", actorId.toString());
        if (record.getTargetId() != null) {
            payload.put("targetId", record.getTargetId().toString());
        }
        domainEventService.record(job.getWorkspaceId(), "import_job_record", record.getId(), "import_job_record.conflict_resolved", payload);
    }

    private void recordConflictResolutionJobEvent(ImportConflictResolutionJob job, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("conflictResolutionJobId", job.getId().toString())
                .put("importJobId", job.getImportJobId().toString())
                .put("resolution", job.getResolution())
                .put("scope", job.getScope())
                .put("status", job.getStatus())
                .put("expectedCount", safeCount(job.getExpectedCount()))
                .put("matchedCount", safeCount(job.getMatchedCount()))
                .put("resolvedCount", safeCount(job.getResolvedCount()))
                .put("failedCount", safeCount(job.getFailedCount()));
        if (actorId != null) {
            payload.put("actorUserId", actorId.toString());
        }
        if (job.getStatusFilter() != null) {
            payload.put("statusFilter", job.getStatusFilter());
        }
        if (job.getConflictStatusFilter() != null) {
            payload.put("conflictStatusFilter", job.getConflictStatusFilter());
        }
        if (job.getSourceTypeFilter() != null) {
            payload.put("sourceTypeFilter", job.getSourceTypeFilter());
        }
        if (job.getErrorMessage() != null) {
            payload.put("errorMessage", job.getErrorMessage());
        }
        domainEventService.record(job.getWorkspaceId(), "import_conflict_resolution_job", job.getId(), eventType, payload);
    }

    private void recordImportJobVersionDiffExportEvent(ImportJob job, ExportJob exportJob, Attachment attachment, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("importJobId", job.getId().toString())
                .put("exportJobId", exportJob.getId().toString())
                .put("fileAttachmentId", attachment.getId().toString())
                .put("filename", attachment.getFilename())
                .put("actorUserId", actorId.toString());
        domainEventService.record(job.getWorkspaceId(), "import_job", job.getId(), "import_job.version_diff_export_created", payload);
    }

    private void recordImportJobRecordVersion(ImportJobRecord record, String changeType, UUID actorId) {
        ImportJobRecordVersion version = new ImportJobRecordVersion();
        version.setImportJobRecordId(record.getId());
        version.setImportJobId(record.getImportJobId());
        version.setVersion(nextImportJobRecordVersion(record.getId()));
        version.setChangeType(changeType);
        version.setChangedById(actorId);
        version.setSnapshot(importJobRecordSnapshot(record));
        importJobRecordVersionRepository.save(version);
    }

    private int nextImportJobRecordVersion(UUID recordId) {
        return importJobRecordVersionRepository.findFirstByImportJobRecordIdOrderByVersionDesc(recordId)
                .map(version -> version.getVersion() + 1)
                .orElse(1);
    }

    private ObjectNode importJobRecordSnapshot(ImportJobRecord record) {
        ObjectNode snapshot = objectMapper.createObjectNode()
                .put("importJobId", record.getImportJobId().toString())
                .put("sourceType", record.getSourceType())
                .put("sourceId", record.getSourceId())
                .put("status", record.getStatus());
        if (record.getTargetType() != null) {
            snapshot.put("targetType", record.getTargetType());
        }
        if (record.getTargetId() != null) {
            snapshot.put("targetId", record.getTargetId().toString());
        }
        if (record.getErrorMessage() != null) {
            snapshot.put("errorMessage", record.getErrorMessage());
        }
        if (record.getConflictStatus() != null) {
            snapshot.put("conflictStatus", record.getConflictStatus());
        }
        if (record.getConflictReason() != null) {
            snapshot.put("conflictReason", record.getConflictReason());
        }
        if (record.getConflictDetectedAt() != null) {
            snapshot.put("conflictDetectedAt", record.getConflictDetectedAt().toString());
        }
        if (record.getConflictResolvedAt() != null) {
            snapshot.put("conflictResolvedAt", record.getConflictResolvedAt().toString());
        }
        if (record.getConflictResolution() != null) {
            snapshot.put("conflictResolution", record.getConflictResolution());
        }
        if (record.getConflictResolvedById() != null) {
            snapshot.put("conflictResolvedById", record.getConflictResolvedById().toString());
        }
        if (record.getConflictMaterializationRunId() != null) {
            snapshot.put("conflictMaterializationRunId", record.getConflictMaterializationRunId().toString());
        }
        snapshot.set("rawPayload", record.getRawPayload() == null ? objectMapper.createObjectNode() : record.getRawPayload().deepCopy());
        return snapshot;
    }

    private ImportJobRecordVersionDiffResponse recordVersionDiff(
            ImportJobRecordVersion current,
            ImportJobRecordVersion previous
    ) {
        Map<String, JsonNode> currentFields = flattenSnapshot(current.getSnapshot());
        Map<String, JsonNode> previousFields = previous == null ? Map.of() : flattenSnapshot(previous.getSnapshot());
        Set<String> paths = new TreeSet<>();
        paths.addAll(currentFields.keySet());
        paths.addAll(previousFields.keySet());
        List<ImportJobRecordFieldDiffResponse> fieldDiffs = new ArrayList<>();
        for (String path : paths) {
            boolean existedBefore = previousFields.containsKey(path);
            boolean existsNow = currentFields.containsKey(path);
            JsonNode previousValue = previousFields.get(path);
            JsonNode currentValue = currentFields.get(path);
            if (!existedBefore) {
                fieldDiffs.add(new ImportJobRecordFieldDiffResponse(path, "added", null, JsonValues.toJavaValue(currentValue)));
            } else if (!existsNow) {
                fieldDiffs.add(new ImportJobRecordFieldDiffResponse(path, "removed", JsonValues.toJavaValue(previousValue), null));
            } else if (!previousValue.equals(currentValue)) {
                fieldDiffs.add(new ImportJobRecordFieldDiffResponse(
                        path,
                        "changed",
                        JsonValues.toJavaValue(previousValue),
                        JsonValues.toJavaValue(currentValue)
                ));
            }
        }
        return new ImportJobRecordVersionDiffResponse(
                current.getId(),
                current.getImportJobRecordId(),
                current.getImportJobId(),
                current.getVersion(),
                previous == null ? null : previous.getVersion(),
                current.getChangeType(),
                current.getChangedById(),
                current.getCreatedAt(),
                fieldDiffs
        );
    }

    private List<ImportJobRecordVersionDiffResponse> recordVersionDiffs(ImportJobRecord record) {
        List<ImportJobRecordVersion> versions = importJobRecordVersionRepository.findByImportJobRecordIdOrderByVersionDesc(record.getId());
        List<ImportJobRecordVersionDiffResponse> responses = new ArrayList<>();
        for (int index = 0; index < versions.size(); index++) {
            ImportJobRecordVersion current = versions.get(index);
            ImportJobRecordVersion previous = index + 1 < versions.size() ? versions.get(index + 1) : null;
            responses.add(recordVersionDiff(current, previous));
        }
        return responses;
    }

    private Map<String, JsonNode> flattenSnapshot(JsonNode snapshot) {
        Map<String, JsonNode> fields = new TreeMap<>();
        flattenSnapshot(snapshot == null ? objectMapper.createObjectNode() : snapshot, "", fields);
        return fields;
    }

    private byte[] jsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize export content", ex);
        }
    }

    private void flattenSnapshot(JsonNode value, String prefix, Map<String, JsonNode> fields) {
        if (value != null && value.isObject()) {
            value.fields().forEachRemaining(entry -> flattenSnapshot(
                    entry.getValue(),
                    prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey(),
                    fields
            ));
            return;
        }
        fields.put(prefix.isBlank() ? "value" : prefix, value == null ? objectMapper.nullNode() : value);
    }

    private void recordMappingTemplateEvent(ImportMappingTemplate template, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("mappingTemplateId", template.getId().toString())
                .put("name", template.getName())
                .put("provider", template.getProvider())
                .put("actorUserId", actorId.toString());
        domainEventService.record(template.getWorkspaceId(), "import_mapping_template", template.getId(), eventType, payload);
    }

    private void recordTransformPresetEvent(ImportTransformPreset preset, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("transformPresetId", preset.getId().toString())
                .put("name", preset.getName())
                .put("version", preset.getVersion() == null ? 1 : preset.getVersion())
                .put("actorUserId", actorId.toString());
        domainEventService.record(preset.getWorkspaceId(), "import_transform_preset", preset.getId(), eventType, payload);
    }

    private void recordTransformPresetCloneEvent(
            ImportTransformPreset clone,
            UUID sourcePresetId,
            ImportTransformPresetVersion sourceVersion,
            UUID actorId
    ) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("transformPresetId", clone.getId().toString())
                .put("name", clone.getName())
                .put("version", clone.getVersion() == null ? 1 : clone.getVersion())
                .put("sourcePresetId", sourcePresetId.toString())
                .put("sourceVersionId", sourceVersion.getId().toString())
                .put("sourceVersion", sourceVersion.getVersion())
                .put("actorUserId", actorId.toString());
        domainEventService.record(clone.getWorkspaceId(), "import_transform_preset", clone.getId(), "import_transform_preset.cloned_from_version", payload);
    }

    private void recordTransformPresetRetargetEvent(
            ImportTransformPreset clone,
            UUID sourcePresetId,
            ImportTransformPresetVersion sourceVersion,
            List<ImportMappingTemplate> templates,
            UUID actorId
    ) {
        ArrayNode templateIds = objectMapper.createArrayNode();
        for (ImportMappingTemplate template : templates) {
            templateIds.add(template.getId().toString());
        }
        ObjectNode payload = objectMapper.createObjectNode()
                .put("transformPresetId", clone.getId().toString())
                .put("name", clone.getName())
                .put("sourcePresetId", sourcePresetId.toString())
                .put("sourceVersionId", sourceVersion.getId().toString())
                .put("sourceVersion", sourceVersion.getVersion())
                .put("retargetedTemplateCount", templates.size())
                .put("actorUserId", actorId.toString());
        payload.set("mappingTemplateIds", templateIds);
        domainEventService.record(clone.getWorkspaceId(), "import_transform_preset", clone.getId(), "import_transform_preset.clone_retargeted", payload);
    }

    private void recordTransformPresetVersion(ImportTransformPreset preset, String changeType, UUID actorId) {
        ImportTransformPresetVersion version = new ImportTransformPresetVersion();
        version.setPresetId(preset.getId());
        version.setWorkspaceId(preset.getWorkspaceId());
        version.setVersion(preset.getVersion() == null ? 1 : preset.getVersion());
        version.setName(preset.getName());
        version.setDescription(preset.getDescription());
        version.setTransformationConfig(preset.getTransformationConfig() == null ? objectMapper.createObjectNode() : preset.getTransformationConfig().deepCopy());
        version.setEnabled(Boolean.TRUE.equals(preset.getEnabled()));
        version.setChangeType(changeType);
        version.setCreatedById(actorId);
        importTransformPresetVersionRepository.save(version);
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

    private String normalizedFilter(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
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

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ParsedImportRecord(String sourceType, String sourceId, JsonNode rawPayload) {
    }

    private record ImportSampleDefinition(
            String key,
            String label,
            String provider,
            String sourceType,
            String description,
            String visibilityPath,
            String content
    ) {
        private ImportSampleResponse response() {
            return new ImportSampleResponse(key, label, provider, sourceType, description);
        }
    }

    private record MaterializedWorkItem(WorkItemResponse response, boolean created) {
    }

    private record ConflictResolutionPreview(
            long matched,
            List<ImportJobRecord> records,
            int page,
            int pageSize,
            boolean hasMore
    ) {
    }

    private record ImportMappingRules(
            List<ImportMappingValueLookup> lookups,
            List<ImportMappingTypeTranslation> typeTranslations,
            List<ImportMappingStatusTranslation> statusTranslations
    ) {
    }
}
