package com.strangequark.trasck.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEventOutboxDispatcher;
import com.strangequark.trasck.event.DomainEventPublished;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WorkItemApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_work_item_test")
            .withUsername("trasck")
            .withPassword("trasck");

    private static final Path ATTACHMENT_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "trasck-work-item-attachments-" + UUID.randomUUID()
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturedDomainEvents capturedDomainEvents;

    @Autowired
    private DomainEventOutboxDispatcher domainEventOutboxDispatcher;

    private String accessToken;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("trasck.attachments.local-root", ATTACHMENT_ROOT::toString);
    }

    @BeforeEach
    void clearCapturedEvents() {
        capturedDomainEvents.clear();
        accessToken = null;
    }

    @Test
    void createsUpdatesRanksTransitionsAssignsAndArchivesWorkItemsWithValidationAndEvents() throws Exception {
        JsonNode setup = postSetup();
        UUID actorId = uuid(setup, "/adminUser/id");
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        UUID memberRoleId = roleId(setup, "member");
        UUID viewerRoleId = roleId(setup, "viewer");
        UUID projectAdminRoleId = roleId(setup, "project_admin");

        assertThat(get("/api/v1/projects/" + projectId + "/work-items").statusCode()).isEqualTo(401);
        accessToken = login(setup);
        String adminAccessToken = accessToken;

        JsonNode firstEpic = createWorkItem(projectId, actorId, "epic", null, "First epic", null);
        UUID firstEpicId = uuid(firstEpic, "/id");
        JsonNode story = createWorkItem(projectId, actorId, "story", firstEpicId, "Implement story", null);
        UUID storyId = uuid(story, "/id");
        ReportingScopeSeed reportingScope = seedReportingScope(workspaceId, projectId, actorId, storyId);

        assertThat(story.at("/key").asText()).endsWith("-2");
        assertThat(story.at("/sequenceNumber").asLong()).isEqualTo(2);
        assertThat(story.at("/workspaceSequenceNumber").asLong()).isEqualTo(2);
        assertThat(story.at("/rank").asText()).hasSize(16);
        assertThat(story.at("/descriptionDocument/type").asText()).isEqualTo("doc");
        assertThat(countWhere("work_item_closure", "descendant_work_item_id", storyId)).isEqualTo(2);
        assertThat(countWhere("work_item_status_history", "work_item_id", storyId)).isEqualTo(1);

        JsonNode estimatedStory = patch("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("estimatePoints", 5.0)
                .put("estimateMinutes", 480)
                .put("remainingMinutes", 300));
        assertThat(estimatedStory.at("/estimatePoints").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(countWhere("work_item_estimate_history", "work_item_id", storyId)).isEqualTo(3);
        JsonNode teamedStory = postJson("/api/v1/work-items/" + storyId + "/team", objectMapper.createObjectNode()
                .put("teamId", reportingScope.teamId().toString()));
        assertThat(uuid(teamedStory, "/teamId")).isEqualTo(reportingScope.teamId());
        assertThat(countWhere("work_item_team_history", "work_item_id", storyId)).isEqualTo(1);

        HttpResponse<String> invalidChildResponse = post(
                "/api/v1/projects/" + projectId + "/work-items",
                workItemBody(actorId, "subtask", firstEpicId, "Invalid subtask", null)
        );
        assertThat(invalidChildResponse.statusCode()).isEqualTo(400);

        JsonNode secondEpic = createWorkItem(projectId, actorId, "epic", null, "Second epic", null);
        UUID secondEpicId = uuid(secondEpic, "/id");
        patch("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("dueDate", "2026-04-20")
                .put("priorityKey", "critical"));
        patch("/api/v1/work-items/" + secondEpicId, objectMapper.createObjectNode()
                .put("dueDate", "2026-05-01")
                .put("priorityKey", "low"));
        JsonNode updatedStory = patch("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("title", "Implement story with parent change")
                .put("parentId", secondEpicId.toString()));
        assertThat(updatedStory.at("/parentId").asText()).isEqualTo(secondEpicId.toString());
        assertThat(countAncestor(firstEpicId, storyId)).isZero();
        assertThat(countAncestor(secondEpicId, storyId)).isEqualTo(1);

        HttpResponse<String> invalidTypeChange = patchResponse("/api/v1/work-items/" + secondEpicId, objectMapper.createObjectNode()
                .put("typeKey", "story"));
        assertThat(invalidTypeChange.statusCode()).isEqualTo(400);

        JsonNode topLevelStory = patch("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("clearParent", true));
        assertThat(topLevelStory.at("/parentId").isMissingNode() || topLevelStory.at("/parentId").isNull()).isTrue();
        assertThat(countAncestor(secondEpicId, storyId)).isZero();
        assertThat(countWhere("work_item_closure", "descendant_work_item_id", storyId)).isEqualTo(1);

        JsonNode assignedStory = postJson("/api/v1/work-items/" + storyId + "/assign", objectMapper.createObjectNode()
                .put("assigneeId", actorId.toString()));
        assertThat(assignedStory.at("/assigneeId").asText()).isEqualTo(actorId.toString());
        assertThat(countWhere("work_item_assignment_history", "work_item_id", storyId)).isEqualTo(1);

        JsonNode rankedStory = postJson("/api/v1/work-items/" + storyId + "/rank", objectMapper.createObjectNode()
                .put("previousWorkItemId", secondEpicId.toString()));
        assertThat(rankedStory.at("/rank").asText()).isGreaterThan(secondEpic.at("/rank").asText());

        JsonNode readyStory = postJson("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "open_to_ready"));
        assertThat(readyStory.at("/statusId").asText()).isNotEqualTo(story.at("/statusId").asText());
        assertThat(countWhere("work_item_status_history", "work_item_id", storyId)).isEqualTo(2);

        HttpResponse<String> invalidTransition = post("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "approval_to_done"));
        assertThat(invalidTransition.statusCode()).isEqualTo(400);
        postJson("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "ready_to_in_progress"));
        postJson("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "in_progress_to_in_review"));
        postJson("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "in_review_to_approval"));
        JsonNode doneStory = postJson("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "approval_to_done"));
        assertThat(uuid(doneStory, "/statusId")).isEqualTo(statusId(setup, "done"));
        assertThat(countWhere("work_item_status_history", "work_item_id", storyId)).isEqualTo(6);

        JsonNode projectWorkItems = getJson("/api/v1/projects/" + projectId + "/work-items?limit=2");
        assertThat(projectWorkItems.at("/items")).hasSize(2);
        assertThat(projectWorkItems.at("/hasMore").asBoolean()).isTrue();
        assertThat(projectWorkItems.at("/nextCursor").asText()).isNotBlank();
        JsonNode nextProjectWorkItems = getJson("/api/v1/projects/" + projectId + "/work-items?limit=2&cursor=" + projectWorkItems.at("/nextCursor").asText());
        assertThat(nextProjectWorkItems.at("/items")).hasSize(1);
        assertThat(nextProjectWorkItems.at("/hasMore").asBoolean()).isFalse();

        JsonNode customerField = postJson("/api/v1/workspaces/" + workspaceId + "/custom-fields", objectMapper.createObjectNode()
                .put("name", "Customer")
                .put("key", "customer")
                .put("fieldType", "text")
                .put("searchable", true));
        UUID customerFieldId = uuid(customerField, "/id");
        JsonNode customerContext = postJson("/api/v1/custom-fields/" + customerFieldId + "/contexts", objectMapper.createObjectNode()
                .put("projectId", projectId.toString())
                .put("workItemTypeId", uuid(story, "/typeId").toString())
                .put("required", false));
        assertThat(uuid(customerContext, "/projectId")).isEqualTo(projectId);
        JsonNode customerValue = putJson("/api/v1/work-items/" + storyId + "/custom-fields/" + customerFieldId, objectMapper.createObjectNode()
                .put("value", "Acme"));
        assertThat(customerValue.at("/fieldKey").asText()).isEqualTo("customer");
        assertThat(customerValue.at("/value").asText()).isEqualTo("Acme");
        assertThat(getJson("/api/v1/work-items/" + storyId + "/custom-fields")).hasSize(1);
        JsonNode filteredByCustomer = getJson("/api/v1/projects/" + projectId + "/work-items?customFieldKey=customer&customFieldValue=Acme");
        assertThat(filteredByCustomer.at("/items")).hasSize(1);
        assertThat(uuid(filteredByCustomer, "/items/0/id")).isEqualTo(storyId);
        JsonNode filteredByCustomerContains = getJson("/api/v1/projects/" + projectId + "/work-items?customFieldKey=customer&customFieldOperator=contains&customFieldValue=cm");
        assertThat(filteredByCustomerContains.at("/items")).hasSize(1);
        assertThat(get("/api/v1/projects/" + projectId + "/work-items?customFieldKey=customer").statusCode()).isEqualTo(400);

        JsonNode impactField = postJson("/api/v1/workspaces/" + workspaceId + "/custom-fields", objectMapper.createObjectNode()
                .put("name", "Business Impact")
                .put("key", "impact")
                .put("fieldType", "number")
                .put("searchable", true));
        UUID impactFieldId = uuid(impactField, "/id");
        putJson("/api/v1/work-items/" + storyId + "/custom-fields/" + impactFieldId, objectMapper.createObjectNode()
                .put("value", 8.0));
        putJson("/api/v1/work-items/" + secondEpicId + "/custom-fields/" + impactFieldId, objectMapper.createObjectNode()
                .put("value", 2.0));
        JsonNode highImpact = getJson("/api/v1/projects/" + projectId + "/work-items?customFieldKey=impact&customFieldOperator=gt&customFieldValue=5");
        assertThat(highImpact.at("/items")).hasSize(1);
        assertThat(uuid(highImpact, "/items/0/id")).isEqualTo(storyId);
        JsonNode impactRange = getJson("/api/v1/projects/" + projectId + "/work-items?customFieldKey=impact&customFieldOperator=between&customFieldValue=2&customFieldValueTo=8");
        assertThat(impactRange.at("/items")).hasSize(2);

        JsonNode skillsField = postJson("/api/v1/workspaces/" + workspaceId + "/custom-fields", objectMapper.createObjectNode()
                .put("name", "Skills")
                .put("key", "skills")
                .put("fieldType", "multi_select")
                .put("searchable", true));
        UUID skillsFieldId = uuid(skillsField, "/id");
        ObjectNode storySkills = objectMapper.createObjectNode();
        storySkills.set("value", objectMapper.createArrayNode().add("backend").add("api"));
        putJson("/api/v1/work-items/" + storyId + "/custom-fields/" + skillsFieldId, storySkills);
        ObjectNode epicSkills = objectMapper.createObjectNode();
        epicSkills.set("value", objectMapper.createArrayNode().add("design"));
        putJson("/api/v1/work-items/" + secondEpicId + "/custom-fields/" + skillsFieldId, epicSkills);
        JsonNode backendSkills = getJson("/api/v1/projects/" + projectId + "/work-items?customFieldKey=skills&customFieldOperator=contains&customFieldValue=backend");
        assertThat(backendSkills.at("/items")).hasSize(1);
        assertThat(uuid(backendSkills, "/items/0/id")).isEqualTo(storyId);
        JsonNode backendOrDesignSkills = getJson("/api/v1/projects/" + projectId + "/work-items?customFieldKey=skills&customFieldOperator=in&customFieldValue=backend,design");
        assertThat(backendOrDesignSkills.at("/items")).hasSize(2);

        JsonNode editScreen = postJson("/api/v1/workspaces/" + workspaceId + "/screens", objectMapper.createObjectNode()
                .put("name", "Story Edit Screen")
                .put("screenType", "work_item"));
        UUID editScreenId = uuid(editScreen, "/id");
        JsonNode screenField = postJson("/api/v1/screens/" + editScreenId + "/fields", objectMapper.createObjectNode()
                .put("customFieldId", customerFieldId.toString())
                .put("position", 10)
                .put("required", false));
        assertThat(uuid(screenField, "/customFieldId")).isEqualTo(customerFieldId);
        JsonNode screenAssignment = postJson("/api/v1/screens/" + editScreenId + "/assignments", objectMapper.createObjectNode()
                .put("projectId", projectId.toString())
                .put("workItemTypeId", uuid(story, "/typeId").toString())
                .put("operation", "edit")
                .put("priority", 10));
        assertThat(screenAssignment.at("/operation").asText()).isEqualTo("edit");
        JsonNode fetchedScreen = getJson("/api/v1/screens/" + editScreenId);
        assertThat(fetchedScreen.at("/fields")).hasSize(1);
        assertThat(fetchedScreen.at("/assignments")).hasSize(1);
        patch("/api/v1/screens/" + editScreenId + "/fields/" + uuid(screenField, "/id"), objectMapper.createObjectNode()
                .put("required", true));
        postJson("/api/v1/screens/" + editScreenId + "/fields", objectMapper.createObjectNode()
                .put("systemFieldKey", "team")
                .put("position", 20)
                .put("required", true));
        postJson("/api/v1/screens/" + editScreenId + "/fields", objectMapper.createObjectNode()
                .put("systemFieldKey", "assignee")
                .put("position", 30)
                .put("required", true));
        ObjectNode clearCustomer = objectMapper.createObjectNode();
        clearCustomer.set("customFields", objectMapper.createObjectNode().putNull("customer"));
        assertThat(patchResponse("/api/v1/work-items/" + storyId, clearCustomer).statusCode()).isEqualTo(400);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/custom-fields")).hasSize(3);
        assertThat(patchResponse("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("clearTeam", true)).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/work-items/" + storyId + "/team", objectMapper.createObjectNode()
                .put("clearTeam", true)).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/work-items/" + storyId + "/assign", objectMapper.createObjectNode()).statusCode()).isEqualTo(400);

        ObjectNode commentBody = objectMapper.createObjectNode()
                .put("bodyMarkdown", "This story needs collaboration coverage.")
                .put("visibility", "workspace");
        commentBody.set("bodyDocument", objectMapper.createObjectNode()
                .put("type", "doc")
                .put("text", "This story needs collaboration coverage."));
        JsonNode comment = postJson("/api/v1/work-items/" + storyId + "/comments", commentBody);
        UUID commentId = uuid(comment, "/id");
        assertThat(comment.at("/bodyDocument/type").asText()).isEqualTo("doc");
        JsonNode comments = getJson("/api/v1/work-items/" + storyId + "/comments");
        assertThat(comments).hasSize(1);
        JsonNode updatedComment = patch("/api/v1/work-items/" + storyId + "/comments/" + commentId, objectMapper.createObjectNode()
                .put("bodyMarkdown", "Updated collaboration note."));
        assertThat(updatedComment.at("/bodyMarkdown").asText()).isEqualTo("Updated collaboration note.");

        JsonNode link = postJson("/api/v1/work-items/" + storyId + "/links", objectMapper.createObjectNode()
                .put("targetWorkItemId", secondEpicId.toString())
                .put("linkType", "relates_to"));
        UUID linkId = uuid(link, "/id");
        assertThat(getJson("/api/v1/work-items/" + storyId + "/links")).hasSize(1);
        HttpResponse<String> duplicateLink = post("/api/v1/work-items/" + storyId + "/links", objectMapper.createObjectNode()
                .put("targetWorkItemId", secondEpicId.toString())
                .put("linkType", "relates_to"));
        assertThat(duplicateLink.statusCode()).isEqualTo(409);

        JsonNode watcher = postJson("/api/v1/work-items/" + storyId + "/watchers", objectMapper.createObjectNode()
                .put("userId", actorId.toString()));
        assertThat(uuid(watcher, "/userId")).isEqualTo(actorId);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/watchers")).hasSize(1);
        HttpResponse<String> invalidWatcher = post("/api/v1/work-items/" + storyId + "/watchers", objectMapper.createObjectNode()
                .put("userId", UUID.randomUUID().toString()));
        assertThat(invalidWatcher.statusCode()).isEqualTo(404);

        ObjectNode workLogBody = objectMapper.createObjectNode()
                .put("userId", actorId.toString())
                .put("minutesSpent", 45)
                .put("workDate", "2026-04-18")
                .put("startedAt", "2026-04-18T14:00:00Z")
                .put("descriptionMarkdown", "Paired on the implementation plan.");
        workLogBody.set("descriptionDocument", objectMapper.createObjectNode()
                .put("type", "doc")
                .put("text", "Paired on the implementation plan."));
        JsonNode workLog = postJson("/api/v1/work-items/" + storyId + "/work-logs", workLogBody);
        UUID workLogId = uuid(workLog, "/id");
        assertThat(workLog.at("/minutesSpent").asInt()).isEqualTo(45);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/work-logs")).hasSize(1);
        HttpResponse<String> invalidWorkLog = post("/api/v1/work-items/" + storyId + "/work-logs", objectMapper.createObjectNode()
                .put("minutesSpent", -1));
        assertThat(invalidWorkLog.statusCode()).isEqualTo(400);
        JsonNode updatedWorkLog = patch("/api/v1/work-items/" + storyId + "/work-logs/" + workLogId, objectMapper.createObjectNode()
                .put("minutesSpent", 60));
        assertThat(updatedWorkLog.at("/minutesSpent").asInt()).isEqualTo(60);

        String viewerEmail = "time-viewer-" + UUID.randomUUID() + "@example.com";
        String viewerPassword = "viewer-password";
        JsonNode viewer = postJson("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", viewerEmail)
                .put("username", "time-viewer-" + UUID.randomUUID())
                .put("displayName", "Time Viewer")
                .put("password", viewerPassword)
                .put("roleId", viewerRoleId.toString())
                .put("emailVerified", true));
        UUID viewerId = uuid(viewer, "/id");
        String memberEmail = "time-member-" + UUID.randomUUID() + "@example.com";
        String memberPassword = "member-password";
        JsonNode member = postJson("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", memberEmail)
                .put("username", "time-member-" + UUID.randomUUID())
                .put("displayName", "Time Member")
                .put("password", memberPassword)
                .put("roleId", memberRoleId.toString())
                .put("emailVerified", true));
        UUID memberId = uuid(member, "/id");

        accessToken = login(viewerEmail, viewerPassword);
        assertThat(post("/api/v1/work-items/" + storyId + "/work-logs", objectMapper.createObjectNode()
                .put("userId", viewerId.toString())
                .put("minutesSpent", 20)
                .put("workDate", "2026-04-18")
                .put("descriptionMarkdown", "Attempted to log time with read permission.")).statusCode()).isEqualTo(403);
        assertThat(patchResponse("/api/v1/work-items/" + storyId + "/work-logs/" + workLogId, objectMapper.createObjectNode()
                .put("minutesSpent", 75)).statusCode()).isEqualTo(403);
        assertThat(delete("/api/v1/work-items/" + storyId + "/work-logs/" + workLogId).statusCode()).isEqualTo(403);

        accessToken = login(memberEmail, memberPassword);
        JsonNode memberWorkLog = postJson("/api/v1/work-items/" + storyId + "/work-logs", objectMapper.createObjectNode()
                .put("userId", memberId.toString())
                .put("minutesSpent", 20)
                .put("workDate", "2026-04-18")
                .put("descriptionMarkdown", "Logged my own time with work-log permission."));
        UUID memberWorkLogId = uuid(memberWorkLog, "/id");
        JsonNode updatedMemberWorkLog = patch("/api/v1/work-items/" + storyId + "/work-logs/" + memberWorkLogId, objectMapper.createObjectNode()
                .put("minutesSpent", 30));
        assertThat(updatedMemberWorkLog.at("/minutesSpent").asInt()).isEqualTo(30);
        JsonNode workLogSummary = getJson("/api/v1/reports/work-items/" + storyId + "/work-log-summary");
        assertThat(workLogSummary.at("/entryCount").asInt()).isEqualTo(2);
        assertThat(workLogSummary.at("/totalMinutes").asLong()).isEqualTo(90);
        accessToken = adminAccessToken;
        JsonNode snapshotRun = postJson("/api/v1/reports/workspaces/" + workspaceId + "/snapshots/run?date=2026-04-19", objectMapper.createObjectNode());
        assertThat(snapshotRun.at("/cycleTimeRecords").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(snapshotRun.at("/iterationSnapshots").asInt()).isEqualTo(1);
        assertThat(snapshotRun.at("/velocitySnapshots").asInt()).isEqualTo(1);
        assertThat(snapshotRun.at("/cumulativeFlowSnapshots").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode snapshotBackfill = postJson("/api/v1/reports/workspaces/" + workspaceId + "/snapshots/backfill?fromDate=2026-04-19&toDate=2026-04-19", objectMapper.createObjectNode());
        assertThat(snapshotBackfill.at("/daysProcessed").asInt()).isEqualTo(1);
        JsonNode defaultRetentionPolicy = getJson("/api/v1/reports/workspaces/" + workspaceId + "/snapshot-retention-policy");
        assertThat(defaultRetentionPolicy.at("/rawRetentionDays").asInt()).isEqualTo(730);
        assertThat(defaultRetentionPolicy.at("/weeklyRollupAfterDays").asInt()).isEqualTo(90);
        JsonNode retentionPolicy = putJson("/api/v1/reports/workspaces/" + workspaceId + "/snapshot-retention-policy", objectMapper.createObjectNode()
                .put("rawRetentionDays", 400)
                .put("weeklyRollupAfterDays", 30)
                .put("monthlyRollupAfterDays", 120)
                .put("archiveAfterDays", 800)
                .put("destructivePruningEnabled", false));
        assertThat(retentionPolicy.at("/rawRetentionDays").asInt()).isEqualTo(400);
        assertThat(retentionPolicy.at("/monthlyRollupAfterDays").asInt()).isEqualTo(120);
        assertThat(countWhere("reporting_retention_policies", "workspace_id", workspaceId)).isEqualTo(1);
        JsonNode rollupBackfill = postJson("/api/v1/reports/workspaces/" + workspaceId + "/snapshots/rollups/backfill?fromDate=2026-04-19&toDate=2026-04-25&granularity=daily", objectMapper.createObjectNode());
        assertThat(rollupBackfill.at("/action").asText()).isEqualTo("rollup_backfill");
        assertThat(rollupBackfill.at("/granularity").asText()).isEqualTo("daily");
        assertThat(rollupBackfill.at("/cycleTimeRollups").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(rollupBackfill.at("/iterationRollups").asInt()).isEqualTo(1);
        assertThat(rollupBackfill.at("/velocityRollups").asInt()).isEqualTo(1);
        assertThat(rollupBackfill.at("/cumulativeFlowRollups").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(countWhere("reporting_snapshot_archive_runs", "id", uuid(rollupBackfill, "/archiveRunId"))).isEqualTo(1);
        JsonNode persistedSnapshots = getJson("/api/v1/reports/projects/" + projectId + "/snapshots?fromDate=2026-04-19&toDate=2026-04-19");
        assertThat(persistedSnapshots.at("/iterationSnapshots")).hasSize(1);
        assertThat(persistedSnapshots.at("/velocitySnapshots")).hasSize(1);
        assertThat(persistedSnapshots.at("/cumulativeFlowSnapshots")).isNotEmpty();
        assertThat(persistedSnapshots.at("/series")).isNotEmpty();
        assertThat(persistedSnapshots.at("/rollupSeries")).isNotEmpty();
        JsonNode iterationReport = getJson("/api/v1/reports/iterations/" + reportingScope.iterationId() + "/report?source=both");
        assertThat(iterationReport.at("/source").asText()).isEqualTo("both");
        assertThat(iterationReport.at("/live/scopedWorkItems").asLong()).isEqualTo(1);
        assertThat(iterationReport.at("/snapshots")).hasSize(1);

        JsonNode projectSummary = getJson("/api/v1/reports/projects/" + projectId + "/dashboard-summary?from=2026-04-18T00:00:00Z&to=2026-04-20T00:00:00Z");
        assertThat(projectSummary.at("/scope/scopeType").asText()).isEqualTo("project");
        assertThat(projectSummary.at("/from").asText()).isEqualTo("2026-04-18T00:00:00Z");
        assertThat(projectSummary.at("/to").asText()).isEqualTo("2026-04-20T00:00:00Z");
        assertThat(projectSummary.at("/workItems/total").asLong()).isEqualTo(3);
        assertThat(projectSummary.at("/estimateAndTime/workLogMinutes").asLong()).isEqualTo(90);
        assertThat(projectSummary.at("/estimateAndTime/workLogDeletedBehavior").asText()).isEqualTo("soft_deleted_excluded");
        assertThat(projectSummary.at("/cycleTime/completedWorkItems").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(projectSummary.at("/byStatus")).isNotEmpty();
        assertThat(projectSummary.at("/widgets")).hasSize(8);

        JsonNode teamIterationSummary = getJson("/api/v1/reports/projects/" + projectId + "/dashboard-summary?from=2026-04-18T00:00:00Z&to=2026-04-20T00:00:00Z&teamId=" + reportingScope.teamId() + "&iterationId=" + reportingScope.iterationId());
        assertThat(teamIterationSummary.at("/scope/scopeType").asText()).isEqualTo("iteration");
        assertThat(uuid(teamIterationSummary, "/scope/teamId")).isEqualTo(reportingScope.teamId());
        assertThat(uuid(teamIterationSummary, "/scope/iterationId")).isEqualTo(reportingScope.iterationId());
        assertThat(teamIterationSummary.at("/scope/teamResolutionMode").asText()).isEqualTo("explicit_work_item_team_then_membership_snapshot");
        assertThat(teamIterationSummary.at("/workItems/total").asLong()).isEqualTo(1);
        assertThat(teamIterationSummary.at("/estimateAndTime/estimatePoints").decimalValue()).isEqualByComparingTo("5.0");
        assertThat(teamIterationSummary.at("/estimateAndTime/workLogMinutes").asLong()).isEqualTo(90);

        UUID programId = seedProgram(workspaceId, projectId);
        JsonNode workspacePortfolioSummary = getJson("/api/v1/reports/workspaces/" + workspaceId + "/dashboard-summary?from=2026-04-18T00:00:00Z&to=2026-04-20T00:00:00Z&projectIds=" + projectId);
        assertThat(workspacePortfolioSummary.at("/scope/scopeType").asText()).isEqualTo("workspace");
        assertThat(workspacePortfolioSummary.at("/scope/projectIds")).hasSize(1);
        assertThat(workspacePortfolioSummary.at("/workItems/total").asLong()).isEqualTo(3);
        assertThat(workspacePortfolioSummary.at("/byProject")).isNotEmpty();
        assertThat(workspacePortfolioSummary.at("/byTeam")).isNotEmpty();
        assertThat(workspacePortfolioSummary.at("/byType")).isNotEmpty();
        JsonNode programPortfolioSummary = getJson("/api/v1/reports/programs/" + programId + "/dashboard-summary?from=2026-04-18T00:00:00Z&to=2026-04-20T00:00:00Z");
        assertThat(programPortfolioSummary.at("/scope/scopeType").asText()).isEqualTo("program");
        assertThat(uuid(programPortfolioSummary, "/scope/programId")).isEqualTo(programId);
        assertThat(programPortfolioSummary.at("/byProject")).isNotEmpty();

        ObjectNode dashboardLayout = objectMapper.createObjectNode()
                .put("columns", 12)
                .put("density", "comfortable");
        JsonNode dashboard = postJson("/api/v1/workspaces/" + workspaceId + "/dashboards", objectMapper.createObjectNode()
                .put("name", "Delivery Team Dashboard")
                .put("visibility", "team")
                .put("teamId", reportingScope.teamId().toString())
                .set("layout", dashboardLayout));
        UUID dashboardId = uuid(dashboard, "/id");
        assertThat(uuid(dashboard, "/teamId")).isEqualTo(reportingScope.teamId());
        assertThat(dashboard.at("/visibility").asText()).isEqualTo("team");
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/dashboards")).hasSize(1);
        JsonNode updatedDashboard = patch("/api/v1/dashboards/" + dashboardId, objectMapper.createObjectNode()
                .put("name", "Delivery Team Reporting"));
        assertThat(updatedDashboard.at("/name").asText()).isEqualTo("Delivery Team Reporting");

        ObjectNode widgetQuery = objectMapper.createObjectNode()
                .put("projectId", projectId.toString())
                .put("teamId", reportingScope.teamId().toString())
                .put("iterationId", reportingScope.iterationId().toString());
        ObjectNode widgetConfig = objectMapper.createObjectNode()
                .put("reportType", "project_dashboard_summary");
        widgetConfig.set("query", widgetQuery);
        JsonNode widget = postJson("/api/v1/dashboards/" + dashboardId + "/widgets", objectMapper.createObjectNode()
                .put("widgetType", "work_item_summary")
                .put("title", "Committed Work")
                .put("positionX", 0)
                .put("positionY", 0)
                .put("width", 4)
                .put("height", 2)
                .set("config", widgetConfig));
        UUID widgetId = uuid(widget, "/id");
        assertThat(widget.at("/config/reportType").asText()).isEqualTo("project_dashboard_summary");
        JsonNode renderedDashboard = getJson("/api/v1/dashboards/" + dashboardId + "/render?from=2026-04-18T00:00:00Z&to=2026-04-20T00:00:00Z");
        assertThat(renderedDashboard.at("/dashboard/widgets")).hasSize(1);
        assertThat(renderedDashboard.at("/widgets/0/data/total").asLong()).isEqualTo(1);
        JsonNode updatedWidget = patch("/api/v1/dashboards/" + dashboardId + "/widgets/" + widgetId, objectMapper.createObjectNode()
                .put("title", "Committed Work Items"));
        assertThat(updatedWidget.at("/title").asText()).isEqualTo("Committed Work Items");
        ObjectNode unsafeWidgetConfig = objectMapper.createObjectNode();
        unsafeWidgetConfig.set("query", objectMapper.createObjectNode()
                .set("nested", objectMapper.createObjectNode().put("rawSql", "select 1")));
        assertThat(post("/api/v1/dashboards/" + dashboardId + "/widgets", objectMapper.createObjectNode()
                .put("widgetType", "work_item_summary")
                .set("config", unsafeWidgetConfig)).statusCode()).isEqualTo(400);
        assertThat(delete("/api/v1/dashboards/" + dashboardId + "/widgets/" + widgetId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/dashboards/" + dashboardId).statusCode()).isEqualTo(204);

        ObjectNode unsafeFilterQuery = objectMapper.createObjectNode();
        unsafeFilterQuery.set("nested", objectMapper.createObjectNode().put("sql", "select 1"));
        assertThat(post("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Unsafe filter")
                .set("query", unsafeFilterQuery)).statusCode()).isEqualTo(400);
        JsonNode savedFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Committed team work")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", widgetQuery));
        UUID savedFilterId = uuid(savedFilter, "/id");
        assertThat(savedFilter.at("/visibility").asText()).isEqualTo("project");
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/saved-filters")).isNotEmpty();

        ObjectNode executableFilterQuery = objectMapper.createObjectNode()
                .put("entityType", "work_item")
                .put("projectId", projectId.toString());
        ObjectNode executableWhere = objectMapper.createObjectNode()
                .put("op", "and");
        ArrayNode executableConditions = objectMapper.createArrayNode();
        executableConditions.add(objectMapper.createObjectNode()
                .put("field", "title")
                .put("operator", "contains")
                .put("value", "Implement"));
        executableConditions.add(objectMapper.createObjectNode()
                .put("field", "typeKey")
                .put("operator", "eq")
                .put("value", "story"));
        executableConditions.add(objectMapper.createObjectNode()
                .put("customFieldKey", "customer")
                .put("operator", "eq")
                .put("value", "Acme"));
        executableConditions.add(objectMapper.createObjectNode()
                .put("customFieldKey", "impact")
                .put("operator", "gt")
                .put("value", "5"));
        executableWhere.set("conditions", executableConditions);
        executableFilterQuery.set("where", executableWhere);
        JsonNode executableFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Executable story search")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", executableFilterQuery));
        UUID executableFilterId = uuid(executableFilter, "/id");
        JsonNode executableResults = getJson("/api/v1/saved-filters/" + executableFilterId + "/work-items?limit=10");
        assertThat(executableResults.at("/items")).hasSize(1);
        assertThat(uuid(executableResults, "/items/0/id")).isEqualTo(storyId);

        ObjectNode pagedFilterQuery = objectMapper.createObjectNode()
                .put("entityType", "work_item")
                .put("projectId", projectId.toString());
        pagedFilterQuery.set("sort", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("field", "workspaceSequenceNumber")
                .put("direction", "asc")));
        ObjectNode pagedWhere = objectMapper.createObjectNode()
                .put("op", "or");
        ArrayNode pagedConditions = objectMapper.createArrayNode();
        pagedConditions.add(objectMapper.createObjectNode()
                .put("customFieldKey", "skills")
                .put("operator", "contains")
                .put("value", "backend"));
        pagedConditions.add(objectMapper.createObjectNode()
                .put("customFieldKey", "skills")
                .put("operator", "contains")
                .put("value", "design"));
        pagedWhere.set("conditions", pagedConditions);
        pagedFilterQuery.set("where", pagedWhere);
        JsonNode pagedFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Paged skills search")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", pagedFilterQuery));
        UUID pagedFilterId = uuid(pagedFilter, "/id");
        JsonNode firstSavedFilterPage = getJson("/api/v1/saved-filters/" + pagedFilterId + "/work-items?limit=1");
        assertThat(firstSavedFilterPage.at("/items")).hasSize(1);
        assertThat(firstSavedFilterPage.at("/hasMore").asBoolean()).isTrue();
        assertThat(uuid(firstSavedFilterPage, "/items/0/id")).isEqualTo(storyId);
        JsonNode secondSavedFilterPage = getJson("/api/v1/saved-filters/" + pagedFilterId + "/work-items?limit=1&cursor=" + firstSavedFilterPage.at("/nextCursor").asText());
        assertThat(secondSavedFilterPage.at("/items")).hasSize(1);
        assertThat(secondSavedFilterPage.at("/hasMore").asBoolean()).isFalse();
        assertThat(uuid(secondSavedFilterPage, "/items/0/id")).isEqualTo(secondEpicId);

        ObjectNode invalidExecutableQuery = objectMapper.createObjectNode()
                .put("entityType", "work_item")
                .put("projectId", projectId.toString());
        invalidExecutableQuery.set("filters", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("customFieldKey", "customer")
                .put("operator", "regex")
                .put("value", "Acme")));
        JsonNode invalidExecutableFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Invalid executable search")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", invalidExecutableQuery));
        UUID invalidExecutableFilterId = uuid(invalidExecutableFilter, "/id");
        assertThat(get("/api/v1/saved-filters/" + invalidExecutableFilterId + "/work-items").statusCode()).isEqualTo(400);

        ObjectNode dueDateSortQuery = objectMapper.createObjectNode()
                .put("entityType", "work_item")
                .put("projectId", projectId.toString());
        dueDateSortQuery.set("sort", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("field", "dueDate")
                .put("direction", "asc")));
        JsonNode dueDateSortFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Due date sort")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", dueDateSortQuery));
        UUID dueDateSortFilterId = uuid(dueDateSortFilter, "/id");
        JsonNode dueDateSortResults = getJson("/api/v1/saved-filters/" + dueDateSortFilterId + "/work-items?limit=2");
        assertThat(uuid(dueDateSortResults, "/items/0/id")).isEqualTo(storyId);
        assertThat(uuid(dueDateSortResults, "/items/1/id")).isEqualTo(secondEpicId);

        ObjectNode prioritySortQuery = objectMapper.createObjectNode()
                .put("entityType", "work_item")
                .put("projectId", projectId.toString());
        prioritySortQuery.set("sort", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("field", "priority")
                .put("direction", "desc")));
        JsonNode prioritySortFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Priority sort")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", prioritySortQuery));
        UUID prioritySortFilterId = uuid(prioritySortFilter, "/id");
        JsonNode priorityFirstPage = getJson("/api/v1/saved-filters/" + prioritySortFilterId + "/work-items?limit=1");
        assertThat(uuid(priorityFirstPage, "/items/0/id")).isEqualTo(storyId);
        assertThat(priorityFirstPage.at("/hasMore").asBoolean()).isTrue();
        JsonNode prioritySecondPage = getJson("/api/v1/saved-filters/" + prioritySortFilterId + "/work-items?limit=1&cursor=" + priorityFirstPage.at("/nextCursor").asText());
        assertThat(prioritySecondPage.at("/items")).hasSize(1);

        JsonNode savedView = postJson("/api/v1/workspaces/" + workspaceId + "/personalization/views", objectMapper.createObjectNode()
                .put("name", "My project work")
                .put("viewType", "work_items")
                .put("visibility", "private")
                .set("config", objectMapper.createObjectNode().put("projectId", projectId.toString())));
        UUID savedViewId = uuid(savedView, "/id");
        assertThat(savedView.at("/visibility").asText()).isEqualTo("private");
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/personalization/views")).isNotEmpty();
        JsonNode updatedSavedView = patch("/api/v1/personalization/views/" + savedViewId, objectMapper.createObjectNode()
                .put("name", "My current project work"));
        assertThat(updatedSavedView.at("/name").asText()).isEqualTo("My current project work");
        JsonNode projectSavedView = postJson("/api/v1/workspaces/" + workspaceId + "/personalization/views", objectMapper.createObjectNode()
                .put("name", "Project shared work")
                .put("viewType", "work_items")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("config", objectMapper.createObjectNode().put("projectId", projectId.toString())));
        UUID projectSavedViewId = uuid(projectSavedView, "/id");
        assertThat(uuid(projectSavedView, "/projectId")).isEqualTo(projectId);
        assertThat(getJson("/api/v1/projects/" + projectId + "/personalization/views")).isNotEmpty();
        JsonNode teamSavedView = postJson("/api/v1/workspaces/" + workspaceId + "/personalization/views", objectMapper.createObjectNode()
                .put("name", "Team shared work")
                .put("viewType", "work_items")
                .put("visibility", "team")
                .put("teamId", reportingScope.teamId().toString())
                .set("config", objectMapper.createObjectNode().put("teamId", reportingScope.teamId().toString())));
        UUID teamSavedViewId = uuid(teamSavedView, "/id");
        assertThat(uuid(teamSavedView, "/teamId")).isEqualTo(reportingScope.teamId());
        JsonNode favorite = postJson("/api/v1/workspaces/" + workspaceId + "/personalization/favorites", objectMapper.createObjectNode()
                .put("entityType", "work_item")
                .put("entityId", storyId.toString()));
        UUID favoriteId = uuid(favorite, "/id");
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/personalization/favorites")).hasSize(1);
        JsonNode recentItem = postJson("/api/v1/workspaces/" + workspaceId + "/personalization/recent-items", objectMapper.createObjectNode()
                .put("entityType", "project")
                .put("entityId", projectId.toString()));
        UUID recentItemId = uuid(recentItem, "/id");
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/personalization/recent-items")).hasSize(1);
        ObjectNode reportQueryRequest = objectMapper.createObjectNode()
                .put("queryKey", "committed-team-work")
                .put("name", "Committed team work query")
                .put("queryType", "project_dashboard_summary")
                .put("visibility", "project")
                .put("projectId", projectId.toString());
        reportQueryRequest.set("queryConfig", objectMapper.createObjectNode().put("savedFilterId", savedFilterId.toString()));
        reportQueryRequest.set("parametersSchema", projectDashboardParameterSchema());
        JsonNode reportQuery = postJson("/api/v1/workspaces/" + workspaceId + "/report-query-catalog", reportQueryRequest);
        UUID reportQueryId = uuid(reportQuery, "/id");
        ObjectNode unsafeCatalogConfig = objectMapper.createObjectNode();
        unsafeCatalogConfig.set("nested", objectMapper.createObjectNode().put("rawSql", "select 1"));
        assertThat(post("/api/v1/workspaces/" + workspaceId + "/report-query-catalog", objectMapper.createObjectNode()
                .put("queryKey", "unsafe-query")
                .put("name", "Unsafe query")
                .put("queryType", "project_dashboard_summary")
                .set("queryConfig", unsafeCatalogConfig)).statusCode()).isEqualTo(400);
        ObjectNode invalidParametersSchema = objectMapper.createObjectNode()
                .put("type", "object");
        invalidParametersSchema.set("properties", objectMapper.createObjectNode()
                .set("projectId", objectMapper.createObjectNode().put("type", "unsupported")));
        assertThat(post("/api/v1/workspaces/" + workspaceId + "/report-query-catalog", objectMapper.createObjectNode()
                .put("queryKey", "invalid-parameter-schema")
                .put("name", "Invalid parameter schema")
                .put("queryType", "project_dashboard_summary")
                .set("parametersSchema", invalidParametersSchema)).statusCode()).isEqualTo(400);
        JsonNode projectDashboard = postJson("/api/v1/workspaces/" + workspaceId + "/dashboards", objectMapper.createObjectNode()
                .put("name", "Project Reporting")
                .put("visibility", "project")
                .put("projectId", projectId.toString()));
        UUID projectDashboardId = uuid(projectDashboard, "/id");
        assertThat(uuid(projectDashboard, "/projectId")).isEqualTo(projectId);
        JsonNode catalogWidget = postJson("/api/v1/dashboards/" + projectDashboardId + "/widgets", objectMapper.createObjectNode()
                .put("widgetType", "work_item_summary")
                .put("title", "Catalog committed work")
                .put("positionX", 0)
                .put("positionY", 0)
                .put("width", 4)
                .put("height", 2)
                .set("config", objectMapper.createObjectNode().put("reportQueryId", reportQueryId.toString())));
        assertThat(uuid(catalogWidget, "/id")).isNotNull();
        JsonNode renderedProjectDashboard = getJson("/api/v1/dashboards/" + projectDashboardId + "/render?from=2026-04-18T00:00:00Z&to=2026-04-20T00:00:00Z");
        assertThat(renderedProjectDashboard.at("/widgets/0/data/total").asLong()).isEqualTo(1);
        JsonNode strictReportQuery = postJson("/api/v1/workspaces/" + workspaceId + "/report-query-catalog", objectMapper.createObjectNode()
                .put("queryKey", "strict-runtime-params")
                .put("name", "Strict runtime params")
                .put("queryType", "project_dashboard_summary")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("parametersSchema", projectDashboardParameterSchema()));
        UUID strictReportQueryId = uuid(strictReportQuery, "/id");
        JsonNode strictDashboard = postJson("/api/v1/workspaces/" + workspaceId + "/dashboards", objectMapper.createObjectNode()
                .put("name", "Strict Project Reporting")
                .put("visibility", "project")
                .put("projectId", projectId.toString()));
        UUID strictDashboardId = uuid(strictDashboard, "/id");
        JsonNode strictWidget = postJson("/api/v1/dashboards/" + strictDashboardId + "/widgets", objectMapper.createObjectNode()
                .put("widgetType", "work_item_summary")
                .put("title", "Strict params")
                .put("positionX", 0)
                .put("positionY", 0)
                .put("width", 4)
                .put("height", 2)
                .set("config", objectMapper.createObjectNode().put("reportQueryId", strictReportQueryId.toString())));
        assertThat(uuid(strictWidget, "/id")).isNotNull();
        assertThat(get("/api/v1/dashboards/" + strictDashboardId + "/render").statusCode()).isEqualTo(400);
        JsonNode permissionTeamDashboard = postJson("/api/v1/workspaces/" + workspaceId + "/dashboards", objectMapper.createObjectNode()
                .put("name", "Team Permission Dashboard")
                .put("visibility", "team")
                .put("teamId", reportingScope.teamId().toString()));
        UUID permissionTeamDashboardId = uuid(permissionTeamDashboard, "/id");
        postJson("/api/v1/teams/" + reportingScope.teamId() + "/memberships", objectMapper.createObjectNode()
                .put("userId", memberId.toString())
                .put("role", "member")
                .put("capacityPercent", 100));
        String teamMemberToken = login(memberEmail, memberPassword);
        String viewerToken = login(viewerEmail, viewerPassword);
        accessToken = viewerToken;
        assertThat(get("/api/v1/dashboards/" + permissionTeamDashboardId).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/personalization/views/" + teamSavedViewId).statusCode()).isEqualTo(404);
        accessToken = teamMemberToken;
        assertThat(get("/api/v1/dashboards/" + permissionTeamDashboardId).statusCode()).isEqualTo(200);
        assertThat(getJson("/api/v1/teams/" + reportingScope.teamId() + "/dashboards")).hasSize(1);
        assertThat(get("/api/v1/personalization/views/" + teamSavedViewId).statusCode()).isEqualTo(200);
        assertThat(getJson("/api/v1/teams/" + reportingScope.teamId() + "/personalization/views")).hasSize(1);
        accessToken = adminAccessToken;
        JsonNode projectInvitation = postJson("/api/v1/workspaces/" + workspaceId + "/invitations", objectMapper.createObjectNode()
                .put("email", "project-reporter-" + UUID.randomUUID() + "@example.com")
                .put("projectId", projectId.toString())
                .put("projectRoleId", projectAdminRoleId.toString()));
        JsonNode projectRegistered = objectMapper.readTree(rawPost("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", projectInvitation.at("/email").asText())
                .put("username", "project-reporter-" + UUID.randomUUID())
                .put("displayName", "Project Reporter")
                .put("password", "project-reporter-password")
                .put("invitationToken", projectInvitation.at("/token").asText())).body());
        UUID projectOnlyUserId = uuid(projectRegistered, "/user/id");
        jdbcTemplate.update("delete from workspace_memberships where workspace_id = ? and user_id = ?", workspaceId, projectOnlyUserId);
        String projectOnlyToken = projectRegistered.at("/accessToken").asText();
        accessToken = projectOnlyToken;
        assertThat(get("/api/v1/workspaces/" + workspaceId + "/dashboards").statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/dashboards/" + projectDashboardId).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/saved-filters/" + savedFilterId).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/personalization/views/" + projectSavedViewId).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/report-query-catalog/" + reportQueryId).statusCode()).isEqualTo(200);
        JsonNode projectOnlyExecutedResults = getJson("/api/v1/saved-filters/" + executableFilterId + "/work-items");
        assertThat(projectOnlyExecutedResults.at("/items")).hasSize(1);
        assertThat(uuid(projectOnlyExecutedResults, "/items/0/id")).isEqualTo(storyId);
        assertThat(getJson("/api/v1/projects/" + projectId + "/dashboards")).isNotEmpty();
        assertThat(getJson("/api/v1/projects/" + projectId + "/saved-filters")).isNotEmpty();
        assertThat(getJson("/api/v1/projects/" + projectId + "/personalization/views")).isNotEmpty();
        assertThat(getJson("/api/v1/projects/" + projectId + "/report-query-catalog")).isNotEmpty();
        assertThat(post("/api/v1/workspaces/" + workspaceId + "/dashboards", objectMapper.createObjectNode()
                .put("name", "Project-only workspace dashboard")
                .put("visibility", "workspace")).statusCode()).isEqualTo(403);
        JsonNode projectOnlyDashboard = postJson("/api/v1/workspaces/" + workspaceId + "/dashboards", objectMapper.createObjectNode()
                .put("name", "Project-only dashboard")
                .put("visibility", "project")
                .put("projectId", projectId.toString()));
        UUID projectOnlyDashboardId = uuid(projectOnlyDashboard, "/id");
        accessToken = adminAccessToken;
        JsonNode scopedReadOnlyToken = postJson("/api/v1/auth/tokens/personal", objectMapper.createObjectNode()
                .put("name", "Work item read only dashboard token")
                .set("scopes", objectMapper.createArrayNode().add("work_item.read")));
        accessToken = scopedReadOnlyToken.at("/token").asText();
        assertThat(get("/api/v1/workspaces/" + workspaceId + "/dashboards").statusCode()).isEqualTo(403);
        accessToken = adminAccessToken;
        assertThat(delete("/api/v1/dashboards/" + permissionTeamDashboardId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/dashboards/" + projectOnlyDashboardId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/dashboards/" + strictDashboardId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/report-query-catalog/" + strictReportQueryId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/report-query-catalog/" + reportQueryId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/personalization/recent-items/" + recentItemId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/personalization/favorites/" + favoriteId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/personalization/views/" + savedViewId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/personalization/views/" + projectSavedViewId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/personalization/views/" + teamSavedViewId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/saved-filters/" + invalidExecutableFilterId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/saved-filters/" + prioritySortFilterId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/saved-filters/" + dueDateSortFilterId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/saved-filters/" + pagedFilterId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/saved-filters/" + executableFilterId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/saved-filters/" + savedFilterId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/dashboards/" + projectDashboardId).statusCode()).isEqualTo(204);

        JsonNode statusHistory = getJson("/api/v1/reports/work-items/" + storyId + "/status-history");
        assertThat(statusHistory).hasSize(6);
        JsonNode assignmentHistory = getJson("/api/v1/reports/work-items/" + storyId + "/assignment-history");
        assertThat(assignmentHistory).hasSize(1);
        JsonNode teamHistory = getJson("/api/v1/reports/work-items/" + storyId + "/team-history");
        assertThat(teamHistory).hasSize(1);
        JsonNode estimateHistory = getJson("/api/v1/reports/work-items/" + storyId + "/estimate-history");
        assertThat(estimateHistory).hasSize(3);

        JsonNode label = postJson("/api/v1/workspaces/" + workspaceId + "/labels", objectMapper.createObjectNode()
                .put("name", "backend")
                .put("color", "#3366ff"));
        UUID labelId = uuid(label, "/id");
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/labels")).hasSize(1);
        JsonNode attachedLabel = postJson("/api/v1/work-items/" + storyId + "/labels", objectMapper.createObjectNode()
                .put("labelId", labelId.toString()));
        assertThat(uuid(attachedLabel, "/id")).isEqualTo(labelId);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/labels")).hasSize(1);

        JsonNode attachment = postJson("/api/v1/work-items/" + storyId + "/attachments", objectMapper.createObjectNode()
                .put("filename", "requirements.txt")
                .put("contentType", "text/plain")
                .put("storageKey", "test/requirements.txt")
                .put("sizeBytes", 64)
                .put("checksum", "sha256:test")
                .put("visibility", "restricted"));
        UUID attachmentId = uuid(attachment, "/id");
        assertThat(getJson("/api/v1/work-items/" + storyId + "/attachments")).hasSize(1);
        HttpResponse<String> invalidAttachment = post("/api/v1/work-items/" + storyId + "/attachments", objectMapper.createObjectNode()
                .put("filename", "bad.txt")
                .put("storageKey", "test/bad.txt")
                .put("sizeBytes", -1));
        assertThat(invalidAttachment.statusCode()).isEqualTo(400);

        byte[] attachmentBytes = "Implementation notes\n".getBytes(StandardCharsets.UTF_8);
        JsonNode uploadedAttachment = uploadAttachmentFile(storyId, "implementation-notes.txt", "text/plain", attachmentBytes);
        UUID uploadedAttachmentId = uuid(uploadedAttachment, "/id");
        String uploadedStorageKey = uploadedAttachment.at("/storageKey").asText();
        assertThat(uploadedAttachment.at("/sizeBytes").asLong()).isEqualTo(attachmentBytes.length);
        assertThat(uploadedAttachment.at("/checksum").asText()).startsWith("sha256:");
        assertThat(Files.exists(ATTACHMENT_ROOT.resolve(uploadedStorageKey))).isTrue();
        assertThat(getJson("/api/v1/work-items/" + storyId + "/attachments")).hasSize(2);
        HttpResponse<byte[]> downloadResponse = getBytes("/api/v1/work-items/" + storyId + "/attachments/" + uploadedAttachmentId + "/download");
        assertThat(downloadResponse.statusCode()).isEqualTo(200);
        assertThat(downloadResponse.body()).isEqualTo(attachmentBytes);
        assertThat(downloadResponse.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).contains("implementation-notes.txt"));

        assertThat(delete("/api/v1/work-items/" + storyId + "/attachments/" + uploadedAttachmentId).statusCode()).isEqualTo(204);
        assertThat(Files.exists(ATTACHMENT_ROOT.resolve(uploadedStorageKey))).isFalse();
        assertThat(delete("/api/v1/work-items/" + storyId + "/attachments/" + attachmentId).statusCode()).isEqualTo(204);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/attachments")).isEmpty();
        assertThat(delete("/api/v1/work-items/" + storyId + "/labels/" + labelId).statusCode()).isEqualTo(204);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/labels")).isEmpty();
        assertThat(delete("/api/v1/work-items/" + storyId + "/watchers/" + actorId).statusCode()).isEqualTo(204);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/watchers")).isEmpty();
        assertThat(delete("/api/v1/work-items/" + storyId + "/work-logs/" + workLogId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/work-items/" + storyId + "/work-logs/" + memberWorkLogId).statusCode()).isEqualTo(204);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/work-logs")).isEmpty();
        assertThat(softDeleted("work_logs", workLogId)).isTrue();
        assertThat(softDeleted("work_logs", memberWorkLogId)).isTrue();
        assertThat(delete("/api/v1/work-items/" + storyId + "/links/" + linkId).statusCode()).isEqualTo(204);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/links")).isEmpty();
        assertThat(delete("/api/v1/work-items/" + storyId + "/comments/" + commentId).statusCode()).isEqualTo(204);
        assertThat(getJson("/api/v1/work-items/" + storyId + "/comments")).isEmpty();

        for (int i = 0; i < 3; i++) {
            domainEventOutboxDispatcher.dispatchPending();
        }
        JsonNode workItemActivity = getJson("/api/v1/work-items/" + storyId + "/activity");
        assertThat(eventTypes(workItemActivity)).contains("work_item.created", "work_item.comment_created", "work_item.work_logged", "work_item.attachment_removed");
        JsonNode projectActivity = getJson("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/activity");
        assertThat(eventTypes(projectActivity)).contains("work_item.created", "work_item.updated");
        JsonNode workspaceActivity = getJson("/api/v1/workspaces/" + workspaceId + "/activity");
        assertThat(eventTypes(workspaceActivity)).contains("work_item.created", "label.created");

        ObjectNode replayRequest = objectMapper.createObjectNode()
                .put("includePublished", true);
        replayRequest.set("consumerKeys", objectMapper.createArrayNode().add("activity-projection"));
        JsonNode replay = postJson("/api/v1/workspaces/" + workspaceId + "/domain-events/replay", replayRequest);
        assertThat(replay.at("/deliveriesReset").asInt()).isGreaterThan(0);
        domainEventOutboxDispatcher.dispatchPending();
        assertThat(countActivity("work_item", storyId, "work_item.created")).isEqualTo(1);

        HttpResponse<String> archiveResponse = delete("/api/v1/work-items/" + storyId);
        assertThat(archiveResponse.statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/work-items/" + storyId).statusCode()).isEqualTo(404);

        List<String> persistedEventTypes = jdbcTemplate.queryForList(
                "select event_type from domain_events where aggregate_id = ? order by occurred_at",
                String.class,
                storyId
        );
        assertThat(persistedEventTypes).contains(
                "work_item.created",
                "work_item.parent_changed",
                "work_item.updated",
                "work_item.team_changed",
                "work_item.assigned",
                "work_item.rank_changed",
                "work_item.status_changed",
                "work_item.comment_created",
                "work_item.comment_updated",
                "work_item.comment_deleted",
                "work_item.link_created",
                "work_item.link_deleted",
                "work_item.watcher_added",
                "work_item.watcher_removed",
                "work_item.work_logged",
                "work_item.work_log_updated",
                "work_item.work_log_deleted",
                "work_item.label_added",
                "work_item.label_removed",
                "work_item.attachment_added",
                "work_item.attachment_removed",
                "work_item.archived"
        );
        assertThat(capturedDomainEvents.eventTypes()).containsAll(persistedEventTypes);
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "work-item-" + unique + "@example.com")
                .put("username", "work-item-" + unique)
                .put("displayName", "Work Item Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Work Item Organization")
                .put("slug", "work-item-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Work Item Workspace")
                .put("key", "WI" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", true));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Work Item Project")
                .put("key", "WIP" + unique.substring(0, 6))
                .put("description", "Project created by work item integration test")
                .put("visibility", "public"));
        HttpResponse<String> response = rawPost("/api/v1/setup", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private String login(JsonNode setup) throws Exception {
        return login(setup.at("/adminUser/email").asText(), "correct-horse-battery-staple");
    }

    private String login(String identifier, String password) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("identifier", identifier)
                .put("password", password);
        HttpResponse<String> response = rawPost("/api/v1/auth/login", body);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie")).hasValueSatisfying(cookie -> assertThat(cookie).contains("HttpOnly"));
        return objectMapper.readTree(response.body()).at("/accessToken").asText();
    }

    private JsonNode createWorkItem(UUID projectId, UUID actorId, String typeKey, UUID parentId, String title, UUID assigneeId) throws Exception {
        HttpResponse<String> response = post("/api/v1/projects/" + projectId + "/work-items", workItemBody(actorId, typeKey, parentId, title, assigneeId));
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private ObjectNode projectDashboardParameterSchema() {
        ObjectNode schema = objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
        schema.set("required", objectMapper.createArrayNode()
                .add("projectId")
                .add("teamId")
                .add("iterationId"));
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("projectId", objectMapper.createObjectNode().put("type", "uuid"));
        properties.set("teamId", objectMapper.createObjectNode().put("type", "uuid"));
        properties.set("iterationId", objectMapper.createObjectNode().put("type", "uuid"));
        schema.set("properties", properties);
        return schema;
    }

    private ObjectNode workItemBody(UUID actorId, String typeKey, UUID parentId, String title, UUID assigneeId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("typeKey", typeKey)
                .put("title", title)
                .put("descriptionMarkdown", "Test work item")
                .put("reporterId", actorId.toString());
        body.set("descriptionDocument", objectMapper.createObjectNode()
                .put("type", "doc")
                .put("title", title));
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId.toString());
        }
        return body;
    }

    private ReportingScopeSeed seedReportingScope(UUID workspaceId, UUID projectId, UUID userId, UUID workItemId) {
        UUID teamId = UUID.randomUUID();
        UUID iterationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into teams (id, workspace_id, name, description, lead_user_id, default_capacity, status) values (?, ?, ?, ?, ?, ?, ?)",
                teamId,
                workspaceId,
                "Delivery Team " + teamId.toString().substring(0, 8),
                "Reporting integration team",
                userId,
                100,
                "active"
        );
        jdbcTemplate.update(
                "insert into project_teams (project_id, team_id, role) values (?, ?, ?)",
                projectId,
                teamId,
                "delivery"
        );
        jdbcTemplate.update(
                "insert into team_memberships (id, team_id, user_id, role, capacity_percent, joined_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                teamId,
                userId,
                "member",
                100,
                java.time.OffsetDateTime.parse("2026-04-01T00:00:00Z")
        );
        jdbcTemplate.update(
                "insert into iterations (id, workspace_id, project_id, team_id, name, start_date, end_date, status, committed_points, completed_points) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                iterationId,
                workspaceId,
                projectId,
                teamId,
                "Sprint 1",
                java.time.LocalDate.parse("2026-04-18"),
                java.time.LocalDate.parse("2026-04-25"),
                "active",
                5.0,
                0.0
        );
        jdbcTemplate.update(
                "insert into iteration_work_items (iteration_id, work_item_id, added_by_id) values (?, ?, ?)",
                iterationId,
                workItemId,
                userId
        );
        return new ReportingScopeSeed(teamId, iterationId);
    }

    private UUID seedProgram(UUID workspaceId, UUID projectId) {
        UUID programId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into programs (id, workspace_id, name, description, status) values (?, ?, ?, ?, ?)",
                programId,
                workspaceId,
                "Portfolio " + programId.toString().substring(0, 8),
                "Program for cross-project reporting.",
                "active"
        );
        jdbcTemplate.update(
                "insert into program_projects (program_id, project_id, position) values (?, ?, ?)",
                programId,
                projectId,
                0
        );
        return programId;
    }

    private JsonNode postJson(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = post(path, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode putJson(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = put(path, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode patch(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = patchResponse(path, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> patchResponse(String path, JsonNode body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, JsonNode body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, JsonNode body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> rawPost(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode uploadAttachmentFile(UUID workItemId, String filename, String contentType, byte[] content) throws Exception {
        String boundary = "----trasck-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, filename, contentType, content);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/v1/work-items/" + workItemId + "/attachments/files"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        authorize(builder);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private byte[] multipartBody(String boundary, String filename, String contentType, byte[] content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeMultipartText(output, boundary, "visibility", "restricted");
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(content);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void writeMultipartText(ByteArrayOutputStream output, String boundary, String name, String value) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).DELETE();
        authorize(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void authorize(HttpRequest.Builder builder) {
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private UUID uuid(JsonNode node, String pointer) {
        return UUID.fromString(node.at(pointer).asText());
    }

    private UUID roleId(JsonNode setup, String key) {
        for (JsonNode role : setup.at("/seedData/roles")) {
            if (key.equals(role.at("/key").asText())) {
                return UUID.fromString(role.at("/id").asText());
            }
        }
        throw new IllegalStateException("Role not found: " + key);
    }

    private UUID statusId(JsonNode setup, String key) {
        for (JsonNode status : setup.at("/seedData/workflow/statuses")) {
            if (key.equals(status.at("/key").asText())) {
                return UUID.fromString(status.at("/id").asText());
            }
        }
        throw new IllegalStateException("Status not found: " + key);
    }

    private List<String> eventTypes(JsonNode events) {
        List<String> types = new ArrayList<>();
        JsonNode entries = events.has("items") ? events.at("/items") : events;
        entries.forEach(event -> types.add(event.at("/eventType").asText()));
        return types;
    }

    private int countWhere(String table, String column, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private int countActivity(String entityType, UUID entityId, String eventType) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from activity_events where entity_type = ? and entity_id = ? and event_type = ?",
                Integer.class,
                entityType,
                entityId,
                eventType
        );
        return count == null ? 0 : count;
    }

    private boolean softDeleted(String table, UUID id) {
        Boolean deleted = jdbcTemplate.queryForObject(
                "select deleted_at is not null from " + table + " where id = ?",
                Boolean.class,
                id
        );
        return Boolean.TRUE.equals(deleted);
    }

    private int countAncestor(UUID ancestorId, UUID descendantId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from work_item_closure where ancestor_work_item_id = ? and descendant_work_item_id = ?",
                Integer.class,
                ancestorId,
                descendantId
        );
        return count == null ? 0 : count;
    }

    private record ReportingScopeSeed(UUID teamId, UUID iterationId) {
    }

    @TestConfiguration
    static class EventCaptureConfiguration {
        @Bean
        CapturedDomainEvents capturedDomainEvents() {
            return new CapturedDomainEvents();
        }
    }

    static class CapturedDomainEvents {
        private final List<DomainEventPublished> events = new CopyOnWriteArrayList<>();

        @EventListener
        void onDomainEvent(DomainEventPublished event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        List<String> eventTypes() {
            return events.stream().map(DomainEventPublished::eventType).toList();
        }
    }
}
