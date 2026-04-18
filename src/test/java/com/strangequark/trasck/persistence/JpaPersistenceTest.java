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
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
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

        assertThat(tableCount).isEqualTo(104);
        assertThat(permissionCount).isEqualTo(27);
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(101);
        assertThat(repositories).hasSizeGreaterThanOrEqualTo(101);
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
        assertThat(agentMessageRepository.count()).isEqualTo(1);
        assertThat(agentArtifactRepository.count()).isEqualTo(1);
        assertThat(agentTaskRepositoryLinkRepository.count()).isEqualTo(1);
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
