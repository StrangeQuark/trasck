package com.strangequark.trasck.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEventPublished;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturedDomainEvents capturedDomainEvents;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @BeforeEach
    void clearCapturedEvents() {
        capturedDomainEvents.clear();
    }

    @Test
    void createsUpdatesRanksTransitionsAssignsAndArchivesWorkItemsWithValidationAndEvents() throws Exception {
        JsonNode setup = postSetup();
        UUID actorId = uuid(setup, "/adminUser/id");
        UUID projectId = uuid(setup, "/project/id");

        JsonNode firstEpic = createWorkItem(projectId, actorId, "epic", null, "First epic", null);
        UUID firstEpicId = uuid(firstEpic, "/id");
        JsonNode story = createWorkItem(projectId, actorId, "story", firstEpicId, "Implement story", null);
        UUID storyId = uuid(story, "/id");

        assertThat(story.at("/key").asText()).endsWith("-2");
        assertThat(story.at("/sequenceNumber").asLong()).isEqualTo(2);
        assertThat(story.at("/workspaceSequenceNumber").asLong()).isEqualTo(2);
        assertThat(story.at("/rank").asText()).hasSize(16);
        assertThat(countWhere("work_item_closure", "descendant_work_item_id", storyId)).isEqualTo(2);
        assertThat(countWhere("work_item_status_history", "work_item_id", storyId)).isEqualTo(1);

        HttpResponse<String> invalidChildResponse = post(
                "/api/v1/projects/" + projectId + "/work-items",
                workItemBody(actorId, "subtask", firstEpicId, "Invalid subtask", null)
        );
        assertThat(invalidChildResponse.statusCode()).isEqualTo(400);

        JsonNode secondEpic = createWorkItem(projectId, actorId, "epic", null, "Second epic", null);
        UUID secondEpicId = uuid(secondEpic, "/id");
        JsonNode updatedStory = patch("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("title", "Implement story with parent change")
                .put("parentId", secondEpicId.toString())
                .put("actorUserId", actorId.toString()));
        assertThat(updatedStory.at("/parentId").asText()).isEqualTo(secondEpicId.toString());
        assertThat(countAncestor(firstEpicId, storyId)).isZero();
        assertThat(countAncestor(secondEpicId, storyId)).isEqualTo(1);

        HttpResponse<String> invalidTypeChange = patchResponse("/api/v1/work-items/" + secondEpicId, objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("actorUserId", actorId.toString()));
        assertThat(invalidTypeChange.statusCode()).isEqualTo(400);

        JsonNode topLevelStory = patch("/api/v1/work-items/" + storyId, objectMapper.createObjectNode()
                .put("clearParent", true)
                .put("actorUserId", actorId.toString()));
        assertThat(topLevelStory.at("/parentId").isMissingNode() || topLevelStory.at("/parentId").isNull()).isTrue();
        assertThat(countAncestor(secondEpicId, storyId)).isZero();
        assertThat(countWhere("work_item_closure", "descendant_work_item_id", storyId)).isEqualTo(1);

        JsonNode assignedStory = postJson("/api/v1/work-items/" + storyId + "/assign", objectMapper.createObjectNode()
                .put("assigneeId", actorId.toString())
                .put("actorUserId", actorId.toString()));
        assertThat(assignedStory.at("/assigneeId").asText()).isEqualTo(actorId.toString());
        assertThat(countWhere("work_item_assignment_history", "work_item_id", storyId)).isEqualTo(1);

        JsonNode rankedStory = postJson("/api/v1/work-items/" + storyId + "/rank", objectMapper.createObjectNode()
                .put("previousWorkItemId", secondEpicId.toString())
                .put("actorUserId", actorId.toString()));
        assertThat(rankedStory.at("/rank").asText()).isGreaterThan(secondEpic.at("/rank").asText());

        JsonNode readyStory = postJson("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "open_to_ready")
                .put("actorUserId", actorId.toString()));
        assertThat(readyStory.at("/statusId").asText()).isNotEqualTo(story.at("/statusId").asText());
        assertThat(countWhere("work_item_status_history", "work_item_id", storyId)).isEqualTo(2);

        HttpResponse<String> invalidTransition = post("/api/v1/work-items/" + storyId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "approval_to_done")
                .put("actorUserId", actorId.toString()));
        assertThat(invalidTransition.statusCode()).isEqualTo(400);

        JsonNode projectWorkItems = getJson("/api/v1/projects/" + projectId + "/work-items");
        assertThat(projectWorkItems).hasSize(3);

        HttpResponse<String> archiveResponse = delete("/api/v1/work-items/" + storyId + "?actorUserId=" + actorId);
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
                "work_item.assigned",
                "work_item.rank_changed",
                "work_item.status_changed",
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
        HttpResponse<String> response = post("/api/v1/setup", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private JsonNode createWorkItem(UUID projectId, UUID actorId, String typeKey, UUID parentId, String title, UUID assigneeId) throws Exception {
        HttpResponse<String> response = post("/api/v1/projects/" + projectId + "/work-items", workItemBody(actorId, typeKey, parentId, title, assigneeId));
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private ObjectNode workItemBody(UUID actorId, String typeKey, UUID parentId, String title, UUID assigneeId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("typeKey", typeKey)
                .put("title", title)
                .put("descriptionMarkdown", "Test work item")
                .put("actorUserId", actorId.toString())
                .put("reporterId", actorId.toString());
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId.toString());
        }
        return body;
    }

    private JsonNode postJson(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = post(path, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode patch(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = patchResponse(path, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> patchResponse(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).DELETE().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private UUID uuid(JsonNode node, String pointer) {
        return UUID.fromString(node.at(pointer).asText());
    }

    private int countWhere(String table, String column, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
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
