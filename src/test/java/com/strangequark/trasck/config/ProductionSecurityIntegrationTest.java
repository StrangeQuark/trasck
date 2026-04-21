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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("trasck.security.jwt-secret", () -> "prod-test-jwt-secret-that-is-long-enough-123456");
        registry.add("trasck.secrets.encryption-key", () -> "prod-test-encryption-key-material-123456");
        registry.add("trasck.security.oauth-assertion-secret", () -> "prod-test-oauth-assertion-secret-123456");
        registry.add("trasck.security.cookie-secure", () -> "true");
        registry.add("trasck.security.oauth-success-redirect", () -> "https://app.example.test/auth/callback");
        registry.add("cors.allowed-origins", () -> "https://app.example.test");
        registry.add("trasck.events.outbox.fixed-delay-ms", () -> "600000");
    }

    @Test
    void protectsOpenApiDocsBehindAuthenticatedWorkspaceAdminInProductionProfiles() throws Exception {
        assertThat(get("/v3/api-docs", null).statusCode()).isIn(401, 403);

        JsonNode setup = postSetup();
        String accessToken = login(setup);

        HttpResponse<String> authenticatedOpenApi = get("/v3/api-docs", accessToken);
        assertThat(authenticatedOpenApi.statusCode()).isEqualTo(200);
        JsonNode openApi = objectMapper.readTree(authenticatedOpenApi.body());
        assertThat(openApi.at("/openapi").asText()).startsWith("3.");
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
        return objectMapper.readTree(response.body());
    }

    private String login(JsonNode setup) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", setup.at("/adminUser/email").asText())
                .put("password", "correct-horse-battery-staple"), null);
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

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
