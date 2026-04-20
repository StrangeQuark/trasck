package com.strangequark.trasck.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.activity.Comment;
import com.strangequark.trasck.activity.CommentRepository;
import com.strangequark.trasck.activity.WorkLog;
import com.strangequark.trasck.activity.WorkLogRepository;
import com.strangequark.trasck.agent.AgentArtifact;
import com.strangequark.trasck.agent.AgentArtifactRepository;
import com.strangequark.trasck.agent.AgentDispatchAttempt;
import com.strangequark.trasck.agent.AgentDispatchAttemptRepository;
import com.strangequark.trasck.agent.AgentMessage;
import com.strangequark.trasck.agent.AgentMessageRepository;
import com.strangequark.trasck.agent.AgentProfile;
import com.strangequark.trasck.agent.AgentProfileRepository;
import com.strangequark.trasck.agent.AgentProvider;
import com.strangequark.trasck.agent.AgentProviderCredential;
import com.strangequark.trasck.agent.AgentProviderCredentialRepository;
import com.strangequark.trasck.agent.AgentProviderRepository;
import com.strangequark.trasck.agent.AgentTask;
import com.strangequark.trasck.agent.AgentTaskEvent;
import com.strangequark.trasck.agent.AgentTaskEventRepository;
import com.strangequark.trasck.agent.AgentTaskRepository;
import com.strangequark.trasck.agent.AgentTaskRepositoryLink;
import com.strangequark.trasck.agent.AgentTaskRepositoryLinkRepository;
import com.strangequark.trasck.agent.RepositoryConnection;
import com.strangequark.trasck.agent.RepositoryConnectionRepository;
import com.strangequark.trasck.automation.AutomationWorkerSettings;
import com.strangequark.trasck.automation.AutomationWorkerSettingsRepository;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import com.strangequark.trasck.integration.EmailProviderSettings;
import com.strangequark.trasck.integration.EmailProviderSettingsRepository;
import com.strangequark.trasck.integration.ImportConflictResolutionJob;
import com.strangequark.trasck.integration.ImportConflictResolutionJobRepository;
import com.strangequark.trasck.integration.ImportJobRecord;
import com.strangequark.trasck.integration.ImportJobRecordRepository;
import com.strangequark.trasck.integration.ImportJobRecordVersion;
import com.strangequark.trasck.integration.ImportJobRecordVersionRepository;
import com.strangequark.trasck.integration.ImportMaterializationRun;
import com.strangequark.trasck.integration.ImportMaterializationRunRepository;
import com.strangequark.trasck.integration.ImportJob;
import com.strangequark.trasck.integration.ImportJobRepository;
import com.strangequark.trasck.integration.ImportMappingTemplate;
import com.strangequark.trasck.integration.ImportMappingTemplateRepository;
import com.strangequark.trasck.integration.ImportWorkspaceSettings;
import com.strangequark.trasck.integration.ImportWorkspaceSettingsRepository;
import com.strangequark.trasck.integration.ImportTransformPreset;
import com.strangequark.trasck.integration.ImportTransformPresetRepository;
import com.strangequark.trasck.integration.ImportTransformPresetVersion;
import com.strangequark.trasck.integration.ImportTransformPresetVersionRepository;
import com.strangequark.trasck.organization.Organization;
import com.strangequark.trasck.organization.OrganizationRepository;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workflow.Workflow;
import com.strangequark.trasck.workflow.WorkflowRepository;
import com.strangequark.trasck.workflow.WorkflowStatus;
import com.strangequark.trasck.workflow.WorkflowStatusRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workitem.WorkItemType;
import com.strangequark.trasck.workitem.WorkItemTypeRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class JpaPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_test")
            .withUsername("trasck")
            .withPassword("trasck");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WorkItemTypeRepository workItemTypeRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowStatusRepository workflowStatusRepository;

    @Autowired
    private WorkItemRepository workItemRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private AgentProviderRepository agentProviderRepository;

    @Autowired
    private AgentProviderCredentialRepository agentProviderCredentialRepository;

    @Autowired
    private AgentProfileRepository agentProfileRepository;

    @Autowired
    private RepositoryConnectionRepository repositoryConnectionRepository;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private AgentTaskEventRepository agentTaskEventRepository;

    @Autowired
    private AgentMessageRepository agentMessageRepository;

    @Autowired
    private AgentArtifactRepository agentArtifactRepository;

    @Autowired
    private AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository;

    @Autowired
    private AgentDispatchAttemptRepository agentDispatchAttemptRepository;

    @Autowired
    private AutomationWorkerSettingsRepository automationWorkerSettingsRepository;

    @Autowired
    private EmailProviderSettingsRepository emailProviderSettingsRepository;

    @Autowired
    private ImportTransformPresetRepository importTransformPresetRepository;

    @Autowired
    private ImportTransformPresetVersionRepository importTransformPresetVersionRepository;

    @Autowired
    private ImportMaterializationRunRepository importMaterializationRunRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private ImportJobRecordRepository importJobRecordRepository;

    @Autowired
    private ImportJobRecordVersionRepository importJobRecordVersionRepository;

    @Autowired
    private ImportConflictResolutionJobRepository importConflictResolutionJobRepository;

    @Autowired
    private ImportMappingTemplateRepository importMappingTemplateRepository;

    @Autowired
    private ImportWorkspaceSettingsRepository importWorkspaceSettingsRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    void mapsFullSchemaAndRepositories() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'",
                Integer.class
        );
        Integer permissionCount = jdbcTemplate.queryForObject("select count(*) from permissions", Integer.class);
        Map<String, Repository> repositories = applicationContext.getBeansOfType(Repository.class);

        assertThat(tableCount).isEqualTo(131);
        assertThat(permissionCount).isEqualTo(31);
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(128);
        assertThat(repositories).hasSizeGreaterThanOrEqualTo(105);
        assertThat(columnExists("automation_worker_settings", "worker_run_retention_days")).isTrue();
        assertThat(columnExists("automation_worker_settings", "worker_run_pruning_automatic_enabled")).isTrue();
        assertThat(columnExists("automation_worker_settings", "worker_run_pruning_interval_minutes")).isTrue();
        assertThat(columnExists("automation_worker_settings", "import_review_exports_enabled")).isTrue();
        assertThat(columnExists("automation_worker_settings", "agent_dispatch_attempt_retention_days")).isTrue();
        assertThat(columnExists("automation_worker_settings", "agent_dispatch_attempt_pruning_automatic_enabled")).isTrue();
        assertThat(columnExists("email_provider_settings", "smtp_password_encrypted")).isTrue();
        assertThat(columnExists("import_mapping_templates", "transform_preset_id")).isTrue();
        assertThat(columnExists("import_transform_presets", "version")).isTrue();
        assertThat(columnExists("import_transform_preset_versions", "change_type")).isTrue();
        assertThat(columnExists("import_conflict_resolution_jobs", "expected_count")).isTrue();
        assertThat(columnExists("board_swimlanes", "saved_filter_id")).isTrue();
        assertThat(columnExists("import_materialization_runs", "records_skipped")).isTrue();
        assertThat(columnExists("import_materialization_runs", "mapping_rules_snapshot")).isTrue();
        assertThat(columnExists("import_job_records", "conflict_status")).isTrue();
        assertThat(columnExists("import_job_records", "conflict_materialization_run_id")).isTrue();
        assertThat(columnExists("import_job_record_versions", "snapshot")).isTrue();
        assertThat(columnExists("import_workspace_settings", "sample_jobs_enabled")).isTrue();
        assertThat(columnExists("agent_dispatch_attempts", "idempotency_key")).isTrue();
        assertThat(columnExists("export_jobs", "request_payload")).isTrue();
        assertThat(columnExists("export_jobs", "created_at")).isTrue();
    }

    @Test
    void persistsCoreWorkItemGraphWithMarkdownAndJsonBodies() {
        CoreFixture fixture = createCoreFixture("persist");
        ObjectNode descriptionDocument = objectMapper.createObjectNode()
                .put("type", "doc")
                .put("content", "Rich description");

        WorkItem item = new WorkItem();
        item.setWorkspaceId(fixture.workspace.getId());
        item.setProjectId(fixture.project.getId());
        item.setTypeId(fixture.type.getId());
        item.setStatusId(fixture.status.getId());
        item.setReporterId(fixture.user.getId());
        item.setKey("PERSIST-1");
        item.setSequenceNumber(1L);
        item.setWorkspaceSequenceNumber(1L);
        item.setTitle("Persist core work item");
        item.setDescriptionMarkdown("Markdown description");
        item.setDescriptionDocument(descriptionDocument);
        item.setRank("0000001000000000");
        WorkItem savedItem = workItemRepository.saveAndFlush(item);

        ObjectNode commentDocument = objectMapper.createObjectNode()
                .put("type", "comment")
                .put("content", "Rich comment");
        Comment comment = new Comment();
        comment.setWorkItemId(savedItem.getId());
        comment.setAuthorId(fixture.user.getId());
        comment.setBodyMarkdown("Markdown comment");
        comment.setBodyDocument(commentDocument);
        commentRepository.saveAndFlush(comment);

        WorkLog workLog = new WorkLog();
        workLog.setWorkItemId(savedItem.getId());
        workLog.setUserId(fixture.user.getId());
        workLog.setMinutesSpent(30);
        workLog.setWorkDate(LocalDate.now());
        workLog.setDescriptionMarkdown("Investigated schema mapping");
        workLogRepository.saveAndFlush(workLog);

        entityManager.clear();

        WorkItem reloaded = workItemRepository.findByIdAndDeletedAtIsNull(savedItem.getId()).orElseThrow();
        assertThat(reloaded.getDescriptionMarkdown()).isEqualTo("Markdown description");
        assertThat(reloaded.getDescriptionDocument().get("content").asText()).isEqualTo("Rich description");
        assertThat(reloaded.getWorkspace().getKey()).isEqualTo(fixture.workspace.getKey());
        assertThat(reloaded.getProject().getKey()).isEqualTo(fixture.project.getKey());
        assertThat(reloaded.getProject().getWorkspace().getId()).isEqualTo(fixture.workspace.getId());
        assertThat(reloaded.getType().getKey()).isEqualTo(fixture.type.getKey());
        assertThat(reloaded.getStatus().getKey()).isEqualTo(fixture.status.getKey());
        Workflow reloadedWorkflow = workflowRepository.findById(fixture.workflow.getId()).orElseThrow();
        assertThat(reloadedWorkflow.getWorkspace().getId()).isEqualTo(fixture.workspace.getId());
        assertThat(reloadedWorkflow.getStatuses()).extracting(WorkflowStatus::getKey).contains(fixture.status.getKey());
        assertThat(commentRepository.count()).isEqualTo(1);
        assertThat(workLogRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsCrossProjectStructuralParentage() {
        CoreFixture first = createCoreFixture("first");
        Project secondProject = createProject(first.workspace, "Second Project", "SECOND");

        WorkItem parent = createWorkItem(first, first.project, "FIRST-1", 1L);
        WorkItem child = createWorkItem(first, secondProject, "SECOND-1", 1L);
        child.setParentId(parent.getId());

        assertThatThrownBy(() -> workItemRepository.saveAndFlush(child))
                .hasMessageContaining("Structural parentage across projects is not allowed");
    }

    @Test
    void persistsProviderNeutralAgentTaskGraph() {
        CoreFixture fixture = createCoreFixture("agent");
        WorkItem item = createWorkItem(fixture, fixture.project, "AGENT-1", 1L);

        User agentUser = new User();
        agentUser.setEmail("agent-" + UUID.randomUUID() + "@example.com");
        agentUser.setUsername("agent-" + UUID.randomUUID());
        agentUser.setDisplayName("Fake Test Agent");
        agentUser.setAccountType("agent");
        agentUser = userRepository.saveAndFlush(agentUser);

        AgentProvider provider = new AgentProvider();
        provider.setWorkspaceId(fixture.workspace.getId());
        provider.setProviderKey("codex-" + UUID.randomUUID());
        provider.setProviderType("codex");
        provider.setDisplayName("Codex Agent Provider");
        provider.setDispatchMode("webhook_push");
        provider.setCapabilitySchema(objectMapper.createObjectNode().put("coding", true));
        provider.setConfig(objectMapper.createObjectNode().put("mode", "test"));
        provider = agentProviderRepository.saveAndFlush(provider);

        AgentProviderCredential credential = new AgentProviderCredential();
        credential.setProviderId(provider.getId());
        credential.setCredentialType("api_key");
        credential.setEncryptedSecret("encrypted:test-secret");
        credential.setMetadata(objectMapper.createObjectNode().put("owner", "test"));
        agentProviderCredentialRepository.saveAndFlush(credential);

        AgentProfile profile = new AgentProfile();
        profile.setWorkspaceId(fixture.workspace.getId());
        profile.setUserId(agentUser.getId());
        profile.setProviderId(provider.getId());
        profile.setDisplayName("Fake Agent");
        profile.setCapabilities(objectMapper.createObjectNode().put("canEditCode", true));
        profile.setConfig(objectMapper.createObjectNode().put("temperature", 0));
        profile = agentProfileRepository.saveAndFlush(profile);

        RepositoryConnection repositoryConnection = new RepositoryConnection();
        repositoryConnection.setWorkspaceId(fixture.workspace.getId());
        repositoryConnection.setProjectId(fixture.project.getId());
        repositoryConnection.setProvider("git");
        repositoryConnection.setName("trasck-backend");
        repositoryConnection.setRepositoryUrl("https://example.com/trasck.git");
        repositoryConnection.setDefaultBranch("main");
        repositoryConnection.setConfig(objectMapper.createObjectNode().put("checkout", "shallow"));
        repositoryConnection = repositoryConnectionRepository.saveAndFlush(repositoryConnection);

        AgentTask task = new AgentTask();
        task.setWorkspaceId(fixture.workspace.getId());
        task.setWorkItemId(item.getId());
        task.setAgentProfileId(profile.getId());
        task.setProviderId(provider.getId());
        task.setRequestedById(fixture.user.getId());
        task.setStatus("queued");
        task.setDispatchMode("webhook_push");
        task.setExternalTaskId("codex-task-" + UUID.randomUUID());
        task.setContextSnapshot(objectMapper.createObjectNode().put("workItemKey", item.getKey()));
        task.setRequestPayload(objectMapper.createObjectNode().put("instruction", "Implement story"));
        task = agentTaskRepository.saveAndFlush(task);

        AgentTaskEvent event = new AgentTaskEvent();
        event.setAgentTaskId(task.getId());
        event.setEventType("queued");
        event.setSeverity("info");
        event.setMessage("Task queued for Codex provider");
        event.setMetadata(objectMapper.createObjectNode().put("source", "test"));
        agentTaskEventRepository.saveAndFlush(event);

        AgentDispatchAttempt dispatchAttempt = new AgentDispatchAttempt();
        dispatchAttempt.setWorkspaceId(fixture.workspace.getId());
        dispatchAttempt.setAgentTaskId(task.getId());
        dispatchAttempt.setProviderId(provider.getId());
        dispatchAttempt.setAgentProfileId(profile.getId());
        dispatchAttempt.setWorkItemId(item.getId());
        dispatchAttempt.setRequestedById(fixture.user.getId());
        dispatchAttempt.setAttemptType("dispatch");
        dispatchAttempt.setDispatchMode("webhook_push");
        dispatchAttempt.setProviderType("codex");
        dispatchAttempt.setTransport("provider_hosted_api");
        dispatchAttempt.setStatus("succeeded");
        dispatchAttempt.setExternalTaskId(task.getExternalTaskId());
        dispatchAttempt.setIdempotencyKey("codex:" + task.getId() + ":dispatch");
        dispatchAttempt.setExternalDispatch(true);
        dispatchAttempt.setRequestPayload(objectMapper.createObjectNode().put("agentTaskId", task.getId().toString()));
        dispatchAttempt.setResponsePayload(objectMapper.createObjectNode().put("externalTaskId", task.getExternalTaskId()));
        dispatchAttempt.setStartedAt(OffsetDateTime.now());
        dispatchAttempt.setFinishedAt(OffsetDateTime.now());
        agentDispatchAttemptRepository.saveAndFlush(dispatchAttempt);

        AgentMessage message = new AgentMessage();
        message.setAgentTaskId(task.getId());
        message.setSenderUserId(agentUser.getId());
        message.setSenderType("agent");
        message.setBodyMarkdown("I can take this story.");
        message.setBodyDocument(objectMapper.createObjectNode().put("type", "doc"));
        agentMessageRepository.saveAndFlush(message);

        AgentArtifact artifact = new AgentArtifact();
        artifact.setAgentTaskId(task.getId());
        artifact.setArtifactType("pull_request");
        artifact.setName("Implement AGENT-1");
        artifact.setExternalUrl("https://example.com/trasck/pull/1");
        artifact.setMetadata(objectMapper.createObjectNode().put("branch", "agent/agent-1"));
        agentArtifactRepository.saveAndFlush(artifact);

        AgentTaskRepositoryLink taskRepositoryLink = new AgentTaskRepositoryLink();
        taskRepositoryLink.setAgentTaskId(task.getId());
        taskRepositoryLink.setRepositoryConnectionId(repositoryConnection.getId());
        taskRepositoryLink.setBaseBranch("main");
        taskRepositoryLink.setWorkingBranch("agent/agent-1");
        taskRepositoryLink.setPullRequestUrl("https://example.com/trasck/pull/1");
        taskRepositoryLink.setMetadata(objectMapper.createObjectNode().put("provider", "codex"));
        agentTaskRepositoryLinkRepository.saveAndFlush(taskRepositoryLink);

        entityManager.clear();

        AgentTask reloaded = agentTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getContextSnapshot().get("workItemKey").asText()).isEqualTo("AGENT-1");
        assertThat(agentProviderCredentialRepository.count()).isEqualTo(1);
        assertThat(agentTaskEventRepository.count()).isEqualTo(1);
        assertThat(agentDispatchAttemptRepository.findByAgentTaskIdOrderByStartedAtAscIdAsc(task.getId())).hasSize(1);
        assertThat(agentMessageRepository.count()).isEqualTo(1);
        assertThat(agentArtifactRepository.count()).isEqualTo(1);
        assertThat(agentTaskRepositoryLinkRepository.count()).isEqualTo(1);
    }

    @Test
    void persistsWorkerRetentionAndWorkspaceEmailProviderSettings() {
        CoreFixture fixture = createCoreFixture("ops");

        AutomationWorkerSettings workerSettings = new AutomationWorkerSettings();
        workerSettings.setWorkspaceId(fixture.workspace.getId());
        workerSettings.setAutomationJobsEnabled(false);
        workerSettings.setWebhookDeliveriesEnabled(false);
        workerSettings.setEmailDeliveriesEnabled(false);
        workerSettings.setAutomationLimit(25);
        workerSettings.setWebhookLimit(25);
        workerSettings.setEmailLimit(25);
        workerSettings.setWebhookMaxAttempts(3);
        workerSettings.setEmailMaxAttempts(3);
        workerSettings.setWebhookDryRun(true);
        workerSettings.setEmailDryRun(true);
        workerSettings.setWorkerRunRetentionEnabled(true);
        workerSettings.setWorkerRunRetentionDays(30);
        workerSettings.setWorkerRunExportBeforePrune(true);
        workerSettings.setWorkerRunPruningAutomaticEnabled(true);
        workerSettings.setWorkerRunPruningIntervalMinutes(720);
        workerSettings.setWorkerRunPruningWindowStart(LocalTime.of(1, 0));
        workerSettings.setWorkerRunPruningWindowEnd(LocalTime.of(4, 0));
        automationWorkerSettingsRepository.saveAndFlush(workerSettings);

        EmailProviderSettings emailSettings = new EmailProviderSettings();
        emailSettings.setWorkspaceId(fixture.workspace.getId());
        emailSettings.setProvider("smtp");
        emailSettings.setFromEmail("no-reply@example.com");
        emailSettings.setSmtpHost("smtp.example.com");
        emailSettings.setSmtpPort(587);
        emailSettings.setSmtpUsername("smtp-user");
        emailSettings.setSmtpPasswordEncrypted("aesgcm:v1:test");
        emailSettings.setSmtpStartTlsEnabled(true);
        emailSettings.setSmtpAuthEnabled(true);
        emailSettings.setActive(true);
        emailProviderSettingsRepository.saveAndFlush(emailSettings);

        ImportWorkspaceSettings importSettings = new ImportWorkspaceSettings();
        importSettings.setWorkspaceId(fixture.workspace.getId());
        importSettings.setSampleJobsEnabled(true);
        importWorkspaceSettingsRepository.saveAndFlush(importSettings);

        ImportTransformPreset preset = new ImportTransformPreset();
        preset.setWorkspaceId(fixture.workspace.getId());
        preset.setName("Jira cleanup");
        preset.setDescription("Shared Jira text cleanup");
        ObjectNode presetTransform = objectMapper.createObjectNode();
        presetTransform.set("title", objectMapper.createArrayNode().add("trim"));
        preset.setTransformationConfig(presetTransform);
        preset.setEnabled(true);
        preset.setVersion(1);
        preset = importTransformPresetRepository.saveAndFlush(preset);

        ImportTransformPresetVersion presetVersion = new ImportTransformPresetVersion();
        presetVersion.setPresetId(preset.getId());
        presetVersion.setWorkspaceId(fixture.workspace.getId());
        presetVersion.setVersion(1);
        presetVersion.setName(preset.getName());
        presetVersion.setDescription(preset.getDescription());
        presetVersion.setTransformationConfig(presetTransform);
        presetVersion.setEnabled(true);
        presetVersion.setChangeType("created");
        presetVersion.setCreatedById(fixture.user.getId());
        presetVersion = importTransformPresetVersionRepository.saveAndFlush(presetVersion);

        ImportMappingTemplate template = new ImportMappingTemplate();
        template.setWorkspaceId(fixture.workspace.getId());
        template.setProjectId(fixture.project.getId());
        template.setName("Jira Story");
        template.setProvider("jira");
        template.setSourceType("issue");
        template.setTargetType("work_item");
        template.setWorkItemTypeKey("story");
        template.setTransformPresetId(preset.getId());
        template.setFieldMapping(objectMapper.createObjectNode().put("title", "fields.summary"));
        template.setDefaults(objectMapper.createObjectNode());
        template.setTransformationConfig(objectMapper.createObjectNode());
        template.setEnabled(true);
        template = importMappingTemplateRepository.saveAndFlush(template);

        ImportJob importJob = createImportJob(fixture, "jira");
        ObjectNode mappingRulesSnapshot = objectMapper.createObjectNode();
        mappingRulesSnapshot.set("valueLookups", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("sourceField", "fields.security")
                        .put("sourceValue", "Public")
                        .put("targetField", "visibility")
                        .put("targetValue", "public")));
        mappingRulesSnapshot.set("typeTranslations", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("sourceTypeKey", "Story")
                        .put("targetTypeKey", "story")));
        mappingRulesSnapshot.set("statusTranslations", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode()
                        .put("sourceStatusKey", "To Do")
                        .put("targetStatusKey", "open")));
        ImportMaterializationRun materializationRun = new ImportMaterializationRun();
        materializationRun.setWorkspaceId(fixture.workspace.getId());
        materializationRun.setImportJobId(importJob.getId());
        materializationRun.setMappingTemplateId(template.getId());
        materializationRun.setTransformPresetId(preset.getId());
        materializationRun.setTransformPresetVersion(preset.getVersion());
        materializationRun.setProjectId(fixture.project.getId());
        materializationRun.setRequestedById(fixture.user.getId());
        materializationRun.setUpdateExisting(false);
        materializationRun.setMappingTemplateSnapshot(objectMapper.createObjectNode().put("name", template.getName()));
        materializationRun.setTransformPresetSnapshot(objectMapper.createObjectNode().put("version", preset.getVersion()));
        materializationRun.setTransformationConfigSnapshot(presetTransform);
        materializationRun.setMappingRulesSnapshot(mappingRulesSnapshot);
        materializationRun.setStatus("completed");
        materializationRun.setRecordsProcessed(1);
        materializationRun.setRecordsCreated(1);
        materializationRun.setRecordsUpdated(0);
        materializationRun.setRecordsFailed(0);
        materializationRun.setRecordsSkipped(0);
        materializationRun.setRecordsConflicted(0);
        materializationRun.setFinishedAt(OffsetDateTime.now());
        materializationRun = importMaterializationRunRepository.saveAndFlush(materializationRun);

        ImportJobRecord conflictRecord = new ImportJobRecord();
        conflictRecord.setImportJobId(importJob.getId());
        conflictRecord.setSourceType("issue");
        conflictRecord.setSourceId("JPA-1");
        conflictRecord.setTargetType("work_item");
        conflictRecord.setTargetId(UUID.randomUUID());
        conflictRecord.setStatus("conflict");
        conflictRecord.setErrorMessage("Existing import target would be skipped");
        conflictRecord.setRawPayload(objectMapper.createObjectNode().put("key", "JPA-1"));
        conflictRecord.setConflictStatus("open");
        conflictRecord.setConflictReason("Existing import target would be skipped");
        conflictRecord.setConflictDetectedAt(OffsetDateTime.now());
        conflictRecord.setConflictMaterializationRunId(materializationRun.getId());
        conflictRecord = importJobRecordRepository.saveAndFlush(conflictRecord);
        ImportJobRecordVersion recordVersion = new ImportJobRecordVersion();
        recordVersion.setImportJobRecordId(conflictRecord.getId());
        recordVersion.setImportJobId(importJob.getId());
        recordVersion.setVersion(1);
        recordVersion.setChangeType("updated");
        recordVersion.setChangedById(fixture.user.getId());
        recordVersion.setSnapshot(objectMapper.createObjectNode().put("status", "conflict"));
        recordVersion = importJobRecordVersionRepository.saveAndFlush(recordVersion);

        ImportConflictResolutionJob conflictResolutionJob = new ImportConflictResolutionJob();
        conflictResolutionJob.setWorkspaceId(fixture.workspace.getId());
        conflictResolutionJob.setImportJobId(importJob.getId());
        conflictResolutionJob.setRequestedById(fixture.user.getId());
        conflictResolutionJob.setResolution("update_existing");
        conflictResolutionJob.setScope("filtered");
        conflictResolutionJob.setStatus("queued");
        conflictResolutionJob.setStatusFilter("conflict");
        conflictResolutionJob.setConflictStatusFilter("open");
        conflictResolutionJob.setSourceTypeFilter("issue");
        conflictResolutionJob.setExpectedCount(1);
        conflictResolutionJob.setMatchedCount(1);
        conflictResolutionJob.setResolvedCount(0);
        conflictResolutionJob.setFailedCount(0);
        conflictResolutionJob.setConfirmation("RESOLVE FILTERED CONFLICTS");
        conflictResolutionJob.setRequestedAt(OffsetDateTime.now());
        conflictResolutionJob = importConflictResolutionJobRepository.saveAndFlush(conflictResolutionJob);

        entityManager.clear();

        AutomationWorkerSettings reloadedWorkerSettings = automationWorkerSettingsRepository
                .findById(fixture.workspace.getId())
                .orElseThrow();
        EmailProviderSettings reloadedEmailSettings = emailProviderSettingsRepository
                .findByWorkspaceId(fixture.workspace.getId())
                .orElseThrow();
        assertThat(reloadedWorkerSettings.getWorkerRunRetentionEnabled()).isTrue();
        assertThat(reloadedWorkerSettings.getWorkerRunRetentionDays()).isEqualTo(30);
        assertThat(reloadedWorkerSettings.getWorkerRunPruningAutomaticEnabled()).isTrue();
        assertThat(reloadedWorkerSettings.getWorkerRunPruningIntervalMinutes()).isEqualTo(720);
        assertThat(reloadedWorkerSettings.getWorkerRunPruningWindowStart()).isEqualTo(LocalTime.of(1, 0));
        assertThat(reloadedEmailSettings.getProvider()).isEqualTo("smtp");
        assertThat(reloadedEmailSettings.getSmtpPasswordEncrypted()).isEqualTo("aesgcm:v1:test");
        assertThat(importWorkspaceSettingsRepository.findByWorkspaceId(fixture.workspace.getId()).orElseThrow().getSampleJobsEnabled()).isTrue();
        assertThat(importTransformPresetRepository.findById(preset.getId()).orElseThrow().getVersion()).isEqualTo(1);
        assertThat(importTransformPresetVersionRepository.findById(presetVersion.getId()).orElseThrow().getChangeType()).isEqualTo("created");
        assertThat(importMappingTemplateRepository.findById(template.getId()).orElseThrow().getTransformPresetId()).isEqualTo(preset.getId());
        ImportJob reloadedImportJob = importJobRepository.findById(importJob.getId()).orElseThrow();
        assertThat(reloadedImportJob.getOpenConflictCompletionAccepted()).isFalse();
        assertThat(reloadedImportJob.getOpenConflictCompletionCount()).isZero();
        assertThat(importMaterializationRunRepository.findById(materializationRun.getId()).orElseThrow().getTransformPresetVersion()).isEqualTo(1);
        assertThat(importMaterializationRunRepository.findById(materializationRun.getId()).orElseThrow().getRecordsSkipped()).isZero();
        assertThat(importMaterializationRunRepository.findById(materializationRun.getId()).orElseThrow().getMappingRulesSnapshot().get("typeTranslations")).hasSize(1);
        ImportJobRecord reloadedConflictRecord = importJobRecordRepository.findById(conflictRecord.getId()).orElseThrow();
        assertThat(reloadedConflictRecord.getConflictStatus()).isEqualTo("open");
        assertThat(reloadedConflictRecord.getConflictMaterializationRunId()).isEqualTo(materializationRun.getId());
        ImportJobRecordVersion reloadedRecordVersion = importJobRecordVersionRepository.findById(recordVersion.getId()).orElseThrow();
        assertThat(reloadedRecordVersion.getImportJobRecordId()).isEqualTo(conflictRecord.getId());
        assertThat(reloadedRecordVersion.getSnapshot().get("status").asText()).isEqualTo("conflict");
        ImportConflictResolutionJob reloadedConflictResolutionJob = importConflictResolutionJobRepository
                .findById(conflictResolutionJob.getId())
                .orElseThrow();
        assertThat(reloadedConflictResolutionJob.getResolution()).isEqualTo("update_existing");
        assertThat(reloadedConflictResolutionJob.getExpectedCount()).isEqualTo(1);
    }

    private ImportJob createImportJob(CoreFixture fixture, String provider) {
        ImportJob job = new ImportJob();
        job.setWorkspaceId(fixture.workspace.getId());
        job.setRequestedById(fixture.user.getId());
        job.setProvider(provider);
        job.setStatus("running");
        job.setConfig(objectMapper.createObjectNode());
        return importJobRepository.saveAndFlush(job);
    }

    private CoreFixture createCoreFixture(String keyPrefix) {
        User user = new User();
        user.setEmail(keyPrefix + "-" + UUID.randomUUID() + "@example.com");
        user.setUsername(keyPrefix + "-" + UUID.randomUUID());
        user.setDisplayName("Test User");
        user = userRepository.saveAndFlush(user);

        Organization organization = new Organization();
        organization.setName("Test Organization " + keyPrefix);
        organization.setSlug("org-" + keyPrefix + "-" + UUID.randomUUID());
        organization.setCreatedById(user.getId());
        organization = organizationRepository.saveAndFlush(organization);

        Workspace workspace = new Workspace();
        workspace.setOrganizationId(organization.getId());
        workspace.setName("Test Workspace " + keyPrefix);
        workspace.setKey(("WS" + keyPrefix).replaceAll("[^A-Za-z0-9]", "").toUpperCase());
        workspace = workspaceRepository.saveAndFlush(workspace);

        Project project = createProject(workspace, "Test Project " + keyPrefix, keyPrefix.toUpperCase());

        WorkItemType type = new WorkItemType();
        type.setWorkspaceId(workspace.getId());
        type.setName("Story");
        type.setKey("story-" + keyPrefix);
        type.setHierarchyLevel(100);
        type = workItemTypeRepository.saveAndFlush(type);

        Workflow workflow = new Workflow();
        workflow.setWorkspaceId(workspace.getId());
        workflow.setName("Default " + keyPrefix);
        workflow = workflowRepository.saveAndFlush(workflow);

        WorkflowStatus status = new WorkflowStatus();
        status.setWorkflowId(workflow.getId());
        status.setName("Open");
        status.setKey("open-" + keyPrefix);
        status.setCategory("todo");
        status = workflowStatusRepository.saveAndFlush(status);

        return new CoreFixture(user, organization, workspace, project, type, workflow, status);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = ?
                          and column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private Project createProject(Workspace workspace, String name, String key) {
        Project project = new Project();
        project.setWorkspaceId(workspace.getId());
        project.setName(name);
        project.setKey(key);
        return projectRepository.saveAndFlush(project);
    }

    private WorkItem createWorkItem(CoreFixture fixture, Project project, String key, Long sequenceNumber) {
        WorkItem item = new WorkItem();
        item.setWorkspaceId(fixture.workspace.getId());
        item.setProjectId(project.getId());
        item.setTypeId(fixture.type.getId());
        item.setStatusId(fixture.status.getId());
        item.setKey(key);
        item.setSequenceNumber(sequenceNumber);
        item.setWorkspaceSequenceNumber(nextWorkspaceSequence(fixture.workspace.getId()));
        item.setTitle("Work item " + key);
        item.setRank("0000001000000000");
        return workItemRepository.saveAndFlush(item);
    }

    private Long nextWorkspaceSequence(UUID workspaceId) {
        Long next = jdbcTemplate.queryForObject(
                "select count(*) + 1 from work_items where workspace_id = ?",
                Long.class,
                workspaceId
        );
        return next == null ? 1L : next;
    }

    private record CoreFixture(
            User user,
            Organization organization,
            Workspace workspace,
            Project project,
            WorkItemType type,
            Workflow workflow,
            WorkflowStatus status
    ) {
    }
}
