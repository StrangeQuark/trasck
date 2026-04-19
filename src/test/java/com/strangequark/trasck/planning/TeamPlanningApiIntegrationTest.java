package com.strangequark.trasck.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEventOutboxDispatcher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TeamPlanningApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_planning_test")
            .withUsername("trasck")
            .withPassword("trasck");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainEventOutboxDispatcher domainEventOutboxDispatcher;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    void managesTeamsProjectAssignmentsIterationsCommitmentCarryoverAndPlanningActivity() throws Exception {
        JsonNode setup = postSetup();
        UUID adminUserId = uuid(setup, "/adminUser/id");
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        UUID viewerRoleId = roleId(setup, "viewer");
        String adminToken = login(setup.at("/adminUser/email").asText(), "correct-horse-battery-staple");

        JsonNode viewer = postJson("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", "planning-viewer@example.com")
                .put("username", "planning-viewer")
                .put("displayName", "Planning Viewer")
                .put("password", "correct-horse-battery-staple")
                .put("workspaceRoleId", viewerRoleId.toString()), adminToken);
        UUID viewerId = uuid(viewer, "/id");
        String viewerToken = login("planning-viewer@example.com", "correct-horse-battery-staple");
        assertThat(post("/api/v1/workspaces/" + workspaceId + "/teams", objectMapper.createObjectNode()
                .put("name", "Forbidden Team"), viewerToken).statusCode()).isEqualTo(403);

        JsonNode team = postJson("/api/v1/workspaces/" + workspaceId + "/teams", objectMapper.createObjectNode()
                .put("name", "Platform Team")
                .put("description", "Owns platform delivery")
                .put("leadUserId", adminUserId.toString())
                .put("defaultCapacity", 80), adminToken);
        UUID teamId = uuid(team, "/id");
        assertThat(team.at("/status").asText()).isEqualTo("active");

        JsonNode membership = postJson("/api/v1/teams/" + teamId + "/memberships", objectMapper.createObjectNode()
                .put("userId", viewerId.toString())
                .put("role", "developer")
                .put("capacityPercent", 75), adminToken);
        assertThat(membership.at("/leftAt").isNull()).isTrue();
        assertThat(getJson("/api/v1/teams/" + teamId + "/memberships", adminToken)).hasSize(1);
        assertThat(delete("/api/v1/teams/" + teamId + "/memberships/" + viewerId, adminToken).statusCode()).isEqualTo(204);
        JsonNode removedMemberships = getJson("/api/v1/teams/" + teamId + "/memberships", adminToken);
        assertThat(removedMemberships.get(0).at("/leftAt").isNull()).isFalse();

        JsonNode projectTeam = putJson("/api/v1/projects/" + projectId + "/teams/" + teamId, objectMapper.createObjectNode()
                .put("role", "delivery"), adminToken);
        assertThat(projectTeam.at("/role").asText()).isEqualTo("delivery");
        assertThat(getJson("/api/v1/projects/" + projectId + "/teams", adminToken)).hasSize(1);

        JsonNode completedStory = createStory(projectId, adminUserId, teamId, "Complete setup story", 8, adminToken);
        UUID completedStoryId = uuid(completedStory, "/id");
        JsonNode openStory = createStory(projectId, adminUserId, teamId, "Carry over story", 5, adminToken);
        UUID openStoryId = uuid(openStory, "/id");

        JsonNode iteration = postJson("/api/v1/projects/" + projectId + "/iterations", objectMapper.createObjectNode()
                .put("name", "Sprint 1")
                .put("teamId", teamId.toString())
                .put("startDate", "2026-04-20")
                .put("endDate", "2026-05-01"), adminToken);
        UUID iterationId = uuid(iteration, "/id");
        assertThat(iteration.at("/status").asText()).isEqualTo("planned");
        postJson("/api/v1/iterations/" + iterationId + "/work-items", objectMapper.createObjectNode()
                .put("workItemId", completedStoryId.toString()), adminToken);
        postJson("/api/v1/iterations/" + iterationId + "/work-items", objectMapper.createObjectNode()
                .put("workItemId", openStoryId.toString()), adminToken);
        assertThat(getJson("/api/v1/iterations/" + iterationId + "/work-items", adminToken)).hasSize(2);

        JsonNode committed = postJson("/api/v1/iterations/" + iterationId + "/commit", objectMapper.createObjectNode(), adminToken);
        assertThat(committed.at("/status").asText()).isEqualTo("active");
        assertThat(committed.at("/committedPoints").decimalValue()).isEqualByComparingTo("13.0");

        transitionToDone(completedStoryId, adminToken);
        JsonNode nextIteration = postJson("/api/v1/projects/" + projectId + "/iterations", objectMapper.createObjectNode()
                .put("name", "Sprint 2")
                .put("teamId", teamId.toString())
                .put("startDate", "2026-05-04")
                .put("endDate", "2026-05-15"), adminToken);
        UUID nextIterationId = uuid(nextIteration, "/id");
        JsonNode closed = postJson("/api/v1/iterations/" + iterationId + "/close", objectMapper.createObjectNode()
                .put("carryOverIncomplete", true)
                .put("carryOverIterationId", nextIterationId.toString()), adminToken);
        assertThat(closed.at("/status").asText()).isEqualTo("closed");
        assertThat(closed.at("/completedPoints").decimalValue()).isEqualByComparingTo("8.0");
        JsonNode carriedOverItems = getJson("/api/v1/iterations/" + nextIterationId + "/work-items", adminToken);
        assertThat(carriedOverItems).hasSize(1);
        assertThat(uuid(carriedOverItems.get(0), "/workItemId")).isEqualTo(openStoryId);

        JsonNode scopedSummary = getJson("/api/v1/reports/projects/" + projectId
                + "/dashboard-summary?from=2026-04-19T00:00:00Z&to=2026-05-02T00:00:00Z&teamId=" + teamId
                + "&iterationId=" + iterationId, adminToken);
        assertThat(scopedSummary.at("/scope/scopeType").asText()).isEqualTo("iteration");
        assertThat(scopedSummary.at("/workItems/total").asLong()).isEqualTo(2);

        domainEventOutboxDispatcher.dispatchPending();
        JsonNode workspaceActivity = getJson("/api/v1/workspaces/" + workspaceId + "/activity", adminToken);
        assertThat(workspaceActivity.toString()).contains("team.created", "iteration.closed");
    }

    private JsonNode createStory(UUID projectId, UUID reporterId, UUID teamId, String title, double estimatePoints, String accessToken) throws Exception {
        return postJson("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("reporterId", reporterId.toString())
                .put("teamId", teamId.toString())
                .put("title", title)
                .put("estimatePoints", estimatePoints), accessToken);
    }

    private void transitionToDone(UUID workItemId, String accessToken) throws Exception {
        for (String transition : new String[] {
                "open_to_ready",
                "ready_to_in_progress",
                "in_progress_to_in_review",
                "in_review_to_approval",
                "approval_to_done"
        }) {
            postJson("/api/v1/work-items/" + workItemId + "/transition", objectMapper.createObjectNode()
                    .put("transitionKey", transition), accessToken);
        }
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "planning-admin-" + unique + "@example.com")
                .put("username", "planning-admin-" + unique)
                .put("displayName", "Planning Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Planning Test Org")
                .put("slug", "planning-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Planning Test Workspace")
                .put("key", "PL" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", false));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Planning Test Project")
                .put("key", "PLN" + unique.substring(0, 6))
                .put("description", "Project created by planning integration test")
                .put("visibility", "private"));
        HttpResponse<String> response = post("/api/v1/setup", body, null);
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private String login(String identifier, String password) throws Exception {
        JsonNode login = postJson("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", identifier)
                .put("password", password), null);
        return login.at("/accessToken").asText();
    }

    private JsonNode postJson(String path, JsonNode body, String accessToken) throws Exception {
        HttpResponse<String> response = post(path, body, accessToken);
        assertThat(response.statusCode()).withFailMessage(response.body()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode putJson(String path, JsonNode body, String accessToken) throws Exception {
        HttpResponse<String> response = request("PUT", path, body, accessToken);
        assertThat(response.statusCode()).withFailMessage(response.body()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode getJson(String path, String accessToken) throws Exception {
        HttpResponse<String> response = request("GET", path, null, accessToken);
        assertThat(response.statusCode()).withFailMessage(response.body()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> post(String path, JsonNode body, String accessToken) throws Exception {
        return request("POST", path, body, accessToken);
    }

    private HttpResponse<String> delete(String path, String accessToken) throws Exception {
        return request("DELETE", path, null, accessToken);
    }

    private HttpResponse<String> request(String method, String path, JsonNode body, String accessToken) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, publisher)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
        throw new AssertionError("Role not found: " + key);
    }
}
