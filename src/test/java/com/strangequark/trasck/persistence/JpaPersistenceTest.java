package com.strangequark.trasck.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.activity.Comment;
import com.strangequark.trasck.activity.CommentRepository;
import com.strangequark.trasck.activity.WorkLog;
import com.strangequark.trasck.activity.WorkLogRepository;
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

        assertThat(tableCount).isEqualTo(88);
        assertThat(permissionCount).isEqualTo(16);
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(87);
        assertThat(repositories).hasSizeGreaterThanOrEqualTo(87);
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
        item.setTitle("Persist core work item");
        item.setDescriptionMarkdown("Markdown description");
        item.setDescriptionDocument(descriptionDocument);
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

        WorkItem reloaded = workItemRepository.findById(savedItem.getId()).orElseThrow();
        assertThat(reloaded.getDescriptionMarkdown()).isEqualTo("Markdown description");
        assertThat(reloaded.getDescriptionDocument().get("content").asText()).isEqualTo("Rich description");
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
        item.setTitle("Work item " + key);
        return workItemRepository.saveAndFlush(item);
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
