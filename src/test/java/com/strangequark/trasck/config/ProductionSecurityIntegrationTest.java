package com.strangequark.trasck.config;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers
class ProductionSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_production_security_test")
            .withUsername("trasck")
            .withPassword("prod-test-database-password-123456");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("trasck.security.jwt-secret", () -> "prod-test-jwt-secret-that-is-long-enough-123456");
        registry.add("trasck.secrets.encryption-key", () -> "prod-test-encryption-key-material-123456");
        registry.add("trasck.security.oauth-assertion-secret", () -> "prod-test-oauth-assertion-secret-123456");
        registry.add("trasck.security.cookie-secure", () -> "true");
        registry.add("trasck.security.rate-limit.store", () -> "redis");
        registry.add("trasck.security.system-admin.step-up-window", () -> "PT15M");
        registry.add("trasck.security.oauth-success-redirect", () -> "https://app.example.test/auth/callback");
        registry.add("cors.allowed-origins", () -> "https://app.example.test");
        registry.add("trasck.events.outbox.fixed-delay-ms", () -> "600000");
    }

    @Test
    void protectsOpenApiDocsBehindAuthenticatedSystemAdminInProductionProfiles() throws Exception {
        assertThat(get("/v3/api-docs", null).statusCode()).isIn(401, 403);

        JsonNode setup = postSetup();
        UUID workspaceId = UUID.fromString(setup.at("/workspace/id").asText());
        UUID projectId = UUID.fromString(setup.at("/project/id").asText());
        UUID adminUserId = UUID.fromString(setup.at("/adminUser/id").asText());
        String accessToken = login(setup);
        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*)
                from role_permissions rp
                join permissions p on p.id = rp.permission_id
                where p.key = 'system.admin'
                """,
                Long.class
        )).isZero();

        HttpResponse<String> authenticatedOpenApi = get("/v3/api-docs", accessToken);
        assertThat(authenticatedOpenApi.statusCode()).isEqualTo(200);
        JsonNode openApi = objectMapper.readTree(authenticatedOpenApi.body());
        assertThat(openApi.at("/openapi").asText()).startsWith("3.");

        JsonNode admins = read(get("/api/v1/system-admins", accessToken));
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).at("/userId").asText()).isEqualTo(adminUserId.toString());
        assertThat(admins.get(0).at("/active").asBoolean()).isTrue();
        assertThat(delete("/api/v1/system-admins/not-a-uuid", accessToken).statusCode()).isEqualTo(400);

        JsonNode policy = read(get("/api/v1/workspaces/" + workspaceId + "/security-policy", accessToken));
        assertThat(policy.at("/customPolicy").asBoolean()).isFalse();

        JsonNode updatedPolicy = read(patch("/api/v1/workspaces/" + workspaceId + "/security-policy", objectMapper.createObjectNode()
                .put("importMaxParseBytes", 8)
                .put("importAllowedContentTypes", "text/plain"), accessToken));
        assertThat(updatedPolicy.at("/customPolicy").asBoolean()).isTrue();
        assertThat(updatedPolicy.at("/importMaxParseBytes").asLong()).isEqualTo(8);

        JsonNode importJob = read(post("/api/v1/workspaces/" + workspaceId + "/import-jobs", objectMapper.createObjectNode()
                .put("provider", "csv"), accessToken));
        assertThat(post("/api/v1/import-jobs/" + importJob.at("/id").asText() + "/parse", objectMapper.createObjectNode()
                .put("sourceType", "csv")
                .put("contentType", "text/plain")
                .put("content", "123456789"), accessToken).statusCode()).isEqualTo(413);

        assertThat(patch("/api/v1/workspaces/" + workspaceId + "/security-policy", objectMapper.createObjectNode()
                .put("attachmentMaxUploadBytes", 0), accessToken).statusCode()).isEqualTo(400);

        JsonNode projectPolicy = read(get("/api/v1/projects/" + projectId + "/security-policy", accessToken));
        assertThat(projectPolicy.at("/workspaceCustomPolicy").asBoolean()).isTrue();
        assertThat(projectPolicy.at("/customPolicy").asBoolean()).isFalse();
        JsonNode updatedProjectPolicy = read(patch("/api/v1/projects/" + projectId + "/security-policy", objectMapper.createObjectNode()
                .put("importMaxParseBytes", 4)
                .put("importAllowedContentTypes", "text/plain"), accessToken));
        assertThat(updatedProjectPolicy.at("/customPolicy").asBoolean()).isTrue();
        assertThat(updatedProjectPolicy.at("/importMaxParseBytes").asLong()).isEqualTo(4);
        JsonNode projectImportJob = read(post("/api/v1/workspaces/" + workspaceId + "/import-jobs", objectMapper.createObjectNode()
                .put("provider", "csv")
                .set("config", objectMapper.createObjectNode().put("targetProjectId", projectId.toString())), accessToken));
        assertThat(post("/api/v1/import-jobs/" + projectImportJob.at("/id").asText() + "/parse", objectMapper.createObjectNode()
                .put("sourceType", "csv")
                .put("contentType", "text/plain")
                .put("content", "12345"), accessToken).statusCode()).isEqualTo(413);

        UUID viewerRoleId = roleId(setup, "viewer");
        JsonNode viewer = read(post("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", "prod-viewer-" + UUID.randomUUID() + "@example.com")
                .put("username", "prod-viewer-" + UUID.randomUUID())
                .put("displayName", "Production Viewer")
                .put("password", "correct-horse-battery-staple")
                .put("roleId", viewerRoleId.toString()), accessToken));
        String viewerToken = login(viewer.at("/email").asText(), "correct-horse-battery-staple");
        assertThat(get("/api/v1/workspaces/" + workspaceId + "/security-policy", viewerToken).statusCode()).isEqualTo(403);

        jdbcTemplate.update("update users set last_login_at = now() - interval '2 hours' where id = ?", adminUserId);
        assertThat(post("/api/v1/system-admins", objectMapper.createObjectNode()
                .put("userId", viewer.at("/id").asText()), accessToken).statusCode()).isEqualTo(403);
        accessToken = login(setup);

        JsonNode grantedViewer = read(post("/api/v1/system-admins", objectMapper.createObjectNode()
                .put("userId", viewer.at("/id").asText()), accessToken));
        assertThat(grantedViewer.at("/active").asBoolean()).isTrue();
        assertThat(get("/v3/api-docs", viewerToken).statusCode()).isEqualTo(200);

        JsonNode revokedOriginal = read(delete("/api/v1/system-admins/" + adminUserId, accessToken));
        assertThat(revokedOriginal.at("/active").asBoolean()).isFalse();
        assertThat(get("/v3/api-docs", accessToken).statusCode()).isEqualTo(403);
        assertThat(delete("/api/v1/system-admins/" + viewer.at("/id").asText(), viewerToken).statusCode()).isEqualTo(409);
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "prod-admin-" + unique + "@example.com")
                .put("username", "prod-admin-" + unique)
                .put("displayName", "Production Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Production Organization")
                .put("slug", "production-organization-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Production Workspace")
                .put("key", "PWS" + unique.substring(0, 5))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", false));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Production Project")
                .put("key", "PPR" + unique.substring(0, 5))
                .put("description", "Production profile security test project")
                .put("visibility", "private"));
        HttpResponse<String> response = post("/api/v1/setup", body, null);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode setup = objectMapper.readTree(response.body());
        String accessToken = login(setup);
        JsonNode organization = read(post("/api/v1/organizations", body.get("organization"), accessToken));
        JsonNode workspace = read(post("/api/v1/organizations/" + uuid(organization, "/id") + "/workspaces", body.get("workspace"), accessToken));
        JsonNode project = read(post("/api/v1/workspaces/" + uuid(workspace, "/id") + "/projects", body.get("project"), accessToken));
        ObjectNode result = objectMapper.createObjectNode();
        result.set("adminUser", setup.at("/adminUser"));
        result.set("organization", organization);
        result.set("workspace", workspace);
        result.set("project", project);
        result.set("seedData", project.at("/seedData"));
        return result;
    }

    private String login(JsonNode setup) throws Exception {
        return login(setup.at("/adminUser/email").asText(), "correct-horse-battery-staple");
    }

    private String login(String identifier, String password) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", identifier)
                .put("password", password), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).at("/accessToken").asText();
    }

    private HttpResponse<String> post(String path, JsonNode body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, JsonNode body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).DELETE();
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode read(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
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
        throw new IllegalArgumentException("Role not found: " + key);
    }

    private void authorize(HttpRequest.Builder builder, String accessToken) {
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
