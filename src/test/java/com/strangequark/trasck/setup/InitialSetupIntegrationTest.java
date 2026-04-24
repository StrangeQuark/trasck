package com.strangequark.trasck.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InitialSetupIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_setup_test")
            .withUsername("trasck")
            .withPassword("trasck");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    void createsInitialSetupWithAllDefaultSeedDataAndAnonymousPublicRead() throws Exception {
        assertThat(count("users")).isZero();
        HttpResponse<String> initialStatusResponse = get("/api/v1/setup/status");
        assertThat(initialStatusResponse.statusCode()).isEqualTo(200);
        JsonNode initialStatus = objectMapper.readTree(initialStatusResponse.body());
        assertThat(initialStatus.at("/available").asBoolean()).isTrue();
        assertThat(initialStatus.at("/completed").asBoolean()).isFalse();

        JsonNode publicSetup = postSetup("open", true, "public");
        UUID adminUserId = uuid(publicSetup, "/adminUser/id");
        UUID workspaceId = uuid(publicSetup, "/workspace/id");
        UUID projectId = uuid(publicSetup, "/project/id");
        UUID workflowId = uuid(publicSetup, "/seedData/workflow/id");
        UUID boardId = uuid(publicSetup, "/seedData/board/id");
        HttpResponse<String> completedStatusResponse = get("/api/v1/setup/status");
        assertThat(completedStatusResponse.statusCode()).isEqualTo(200);
        JsonNode completedStatus = objectMapper.readTree(completedStatusResponse.body());
        assertThat(completedStatus.at("/available").asBoolean()).isFalse();
        assertThat(completedStatus.at("/completed").asBoolean()).isTrue();

        assertThat(publicSetup.at("/seedData/workItemTypes")).hasSize(9);
        assertThat(publicSetup.at("/seedData/workItemTypeRules")).hasSize(10);
        assertThat(publicSetup.at("/seedData/priorities")).hasSize(5);
        assertThat(publicSetup.at("/seedData/resolutions")).hasSize(5);
        assertThat(publicSetup.at("/seedData/workflow/statuses")).hasSize(7);
        assertThat(publicSetup.at("/seedData/workflow/transitions")).hasSize(9);
        assertThat(publicSetup.at("/seedData/board/columns")).hasSize(6);
        assertThat(publicSetup.at("/seedData/roles")).hasSize(6);
        assertThat(publicSetup.at("/seedData/projectWorkItemTypes")).hasSize(9);
        assertThat(publicSetup.at("/seedData/workflowAssignments")).hasSize(9);

        assertThat(countWhere("work_item_types", "workspace_id", workspaceId)).isEqualTo(9);
        assertThat(countWhere("work_item_type_rules", "workspace_id", workspaceId)).isEqualTo(10);
        assertThat(countWhere("priorities", "workspace_id", workspaceId)).isEqualTo(5);
        assertThat(countWhere("resolutions", "workspace_id", workspaceId)).isEqualTo(5);
        assertThat(countWhere("workflow_statuses", "workflow_id", workflowId)).isEqualTo(7);
        assertThat(countWhere("workflow_transitions", "workflow_id", workflowId)).isEqualTo(9);
        assertThat(countWhere("workflow_transition_rules", "rule_type", "human_approval_required")).isEqualTo(1);
        assertThat(countWhere("project_work_item_types", "project_id", projectId)).isEqualTo(9);
        assertThat(countWhere("workflow_assignments", "project_id", projectId)).isEqualTo(9);
        assertThat(countWhere("board_columns", "board_id", boardId)).isEqualTo(6);
        assertThat(countWhere("roles", "workspace_id", workspaceId)).isEqualTo(6);
        assertThat(countWhere("workspace_memberships", "workspace_id", workspaceId)).isEqualTo(1);
        assertThat(countWhere("project_memberships", "project_id", projectId)).isEqualTo(1);
        assertThat(countWhere("attachment_storage_configs", "workspace_id", workspaceId)).isEqualTo(1);
        String passwordHash = jdbcTemplate.queryForObject("select password_hash from users where id = ?", String.class, adminUserId);
        assertThat(passwordHash).isNotEqualTo("correct-horse-battery-staple").startsWith("$2");

        HttpResponse<String> publicReadResponse = get("/api/v1/public/projects/" + projectId);
        assertThat(publicReadResponse.statusCode()).isEqualTo(200);
        JsonNode publicProject = objectMapper.readTree(publicReadResponse.body());
        assertThat(publicProject.at("/id").asText()).isEqualTo(projectId.toString());
        assertThat(publicProject.at("/visibility").asText()).isEqualTo("public");

        UUID publicWorkItemId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into work_items (
                    id, workspace_id, project_id, type_id, status_id, reporter_id, created_by_id, updated_by_id,
                    key, sequence_number, workspace_sequence_number, title, description_markdown, visibility, rank
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                publicWorkItemId,
                workspaceId,
                projectId,
                keyedId(publicSetup.at("/seedData/workItemTypes"), "story"),
                keyedId(publicSetup.at("/seedData/workflow/statuses"), "open"),
                adminUserId,
                adminUserId,
                adminUserId,
                "PUB-1",
                1L,
                1L,
                "Public work item",
                "Visible through anonymous public project reads.",
                "inherited",
                "0000001000000000"
        );
        HttpResponse<String> publicWorkItemsResponse = get("/api/v1/public/projects/" + projectId + "/work-items?limit=1");
        assertThat(publicWorkItemsResponse.statusCode()).isEqualTo(200);
        JsonNode publicWorkItems = objectMapper.readTree(publicWorkItemsResponse.body());
        assertThat(publicWorkItems.at("/items")).hasSize(1);
        assertThat(publicWorkItems.at("/items/0/id").asText()).isEqualTo(publicWorkItemId.toString());
        assertThat(publicWorkItems.at("/items/0/title").asText()).isEqualTo("Public work item");
        assertThat(publicWorkItems.at("/items/0").has("assigneeId")).isFalse();
        assertThat(publicWorkItems.at("/items/0").has("reporterId")).isFalse();

        HttpResponse<String> publicWorkItemResponse = get("/api/v1/public/projects/" + projectId + "/work-items/" + publicWorkItemId);
        assertThat(publicWorkItemResponse.statusCode()).isEqualTo(200);
        JsonNode publicWorkItem = objectMapper.readTree(publicWorkItemResponse.body());
        assertThat(publicWorkItem.at("/id").asText()).isEqualTo(publicWorkItemId.toString());
        assertThat(publicWorkItem.at("/key").asText()).isEqualTo("PUB-1");

        jdbcTemplate.update("update work_items set visibility = 'private' where id = ?", publicWorkItemId);
        HttpResponse<String> privateWorkItemResponse = get("/api/v1/public/projects/" + projectId + "/work-items/" + publicWorkItemId);
        assertThat(privateWorkItemResponse.statusCode()).isEqualTo(404);
        HttpResponse<String> privateWorkItemsResponse = get("/api/v1/public/projects/" + projectId + "/work-items");
        assertThat(privateWorkItemsResponse.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(privateWorkItemsResponse.body()).at("/items")).isEmpty();
        jdbcTemplate.update("update work_items set visibility = 'inherited' where id = ?", publicWorkItemId);

        jdbcTemplate.update("update projects set visibility = 'private' where id = ?", projectId);
        HttpResponse<String> privateProjectReadResponse = get("/api/v1/public/projects/" + projectId);
        assertThat(privateProjectReadResponse.statusCode()).isEqualTo(404);
        HttpResponse<String> privateProjectWorkItemsResponse = get("/api/v1/public/projects/" + projectId + "/work-items");
        assertThat(privateProjectWorkItemsResponse.statusCode()).isEqualTo(404);

        jdbcTemplate.update("update projects set visibility = 'public' where id = ?", projectId);
        jdbcTemplate.update("update workspaces set anonymous_read_enabled = false where id = ?", workspaceId);
        HttpResponse<String> closedWorkspaceReadResponse = get("/api/v1/public/projects/" + projectId);
        assertThat(closedWorkspaceReadResponse.statusCode()).isEqualTo(404);

        HttpResponse<String> secondSetupResponse = post("/api/v1/setup", setupBody("second", true, "public"));
        assertThat(secondSetupResponse.statusCode()).isEqualTo(409);
    }

    private JsonNode postSetup(String prefix, boolean anonymousReadEnabled, String projectVisibility) throws Exception {
        ObjectNode body = setupBody(prefix, anonymousReadEnabled, projectVisibility);
        HttpResponse<String> response = post("/api/v1/setup", body);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode responseBody = objectMapper.readTree(response.body());
        assertThat(responseBody.at("/adminUser/accountType").asText()).isEqualTo("human");
        assertThat(responseBody.at("/workspace/anonymousReadEnabled").asBoolean()).isEqualTo(anonymousReadEnabled);
        return responseBody;
    }

    private ObjectNode setupBody(String prefix, boolean anonymousReadEnabled, String projectVisibility) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", prefix + "-" + unique + "@example.com")
                .put("username", prefix + "-" + unique)
                .put("displayName", "Setup Admin " + prefix)
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Organization " + prefix)
                .put("slug", "organization-" + prefix + "-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Workspace " + prefix)
                .put("key", "WS" + prefix + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", anonymousReadEnabled));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Project " + prefix)
                .put("key", "PR" + prefix + unique.substring(0, 6))
                .put("description", "Project created by setup integration test")
                .put("visibility", projectVisibility));
        return body;
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

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private UUID uuid(JsonNode node, String pointer) {
        return UUID.fromString(node.at(pointer).asText());
    }

    private UUID keyedId(JsonNode keyedRows, String key) {
        for (JsonNode row : keyedRows) {
            if (key.equals(row.path("key").asText())) {
                return UUID.fromString(row.path("id").asText());
            }
        }
        throw new AssertionError("Missing keyed setup row: " + key);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private int countWhere(String table, String column, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }
}
