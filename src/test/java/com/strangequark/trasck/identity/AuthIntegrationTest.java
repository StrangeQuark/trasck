package com.strangequark.trasck.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
class AuthIntegrationTest {

    private static final String OAUTH_ASSERTION_SECRET = "test-oauth-assertion-secret-that-is-long-enough";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_auth_test")
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
        registry.add("trasck.security.oauth-assertion-secret", () -> OAUTH_ASSERTION_SECRET);
    }

    @Test
    void supportsCookieAndBearerLoginUserManagementInvitationsOauthLinkingAndPermissionDenial() throws Exception {
        JsonNode setup = postSetup();
        UUID adminUserId = uuid(setup, "/adminUser/id");
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        UUID viewerRoleId = roleId(setup, "viewer");

        assertThat(get("/api/v1/auth/me", null).statusCode()).isEqualTo(401);

        AuthSession admin = login(setup.at("/adminUser/email").asText(), "correct-horse-battery-staple");
        assertThat(admin.accessToken()).isNotBlank();
        assertThat(admin.cookie()).contains("trasck_access_token=").contains("HttpOnly");

        JsonNode meByBearer = read(get("/api/v1/auth/me", admin.accessToken()));
        assertThat(uuid(meByBearer, "/id")).isEqualTo(adminUserId);

        HttpResponse<String> meByCookie = getWithCookie("/api/v1/auth/me", admin.cookie());
        assertThat(meByCookie.statusCode()).isEqualTo(200);

        HttpResponse<String> openRegister = post("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", "no-invite@example.com")
                .put("username", "no-invite")
                .put("displayName", "No Invite")
                .put("password", "correct-horse-battery-staple"), null);
        assertThat(openRegister.statusCode()).isEqualTo(400);

        JsonNode invitation = read(post("/api/v1/workspaces/" + workspaceId + "/invitations", objectMapper.createObjectNode()
                .put("email", "invited@example.com"), admin.accessToken()));
        assertThat(invitation.at("/token").asText()).isNotBlank();

        JsonNode registered = read(post("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", "invited@example.com")
                .put("username", "invited-user")
                .put("displayName", "Invited User")
                .put("password", "correct-horse-battery-staple")
                .put("invitationToken", invitation.at("/token").asText()), null));
        assertThat(registered.at("/accessToken").asText()).isNotBlank();
        assertThat(countWhere("workspace_memberships", "user_id", uuid(registered, "/user/id"))).isEqualTo(1);

        JsonNode viewer = read(post("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", "viewer@example.com")
                .put("username", "viewer-user")
                .put("displayName", "Viewer User")
                .put("password", "correct-horse-battery-staple")
                .put("roleId", viewerRoleId.toString()), admin.accessToken()));
        assertThat(uuid(viewer, "/id")).isNotNull();

        AuthSession viewerSession = login("viewer@example.com", "correct-horse-battery-staple");
        assertThat(get("/api/v1/projects/" + projectId + "/work-items", viewerSession.accessToken()).statusCode()).isEqualTo(200);
        HttpResponse<String> forbiddenCreate = post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Viewer should not create"), viewerSession.accessToken());
        assertThat(forbiddenCreate.statusCode()).isEqualTo(403);

        String provider = "github";
        String providerSubject = "github-admin-subject";
        String providerEmail = setup.at("/adminUser/email").asText();
        JsonNode oauthLogin = read(post("/api/v1/auth/oauth/login", objectMapper.createObjectNode()
                .put("provider", provider)
                .put("providerSubject", providerSubject)
                .put("providerEmail", providerEmail)
                .put("emailVerified", true)
                .put("providerUsername", "setup-admin-github")
                .put("displayName", "Setup Admin")
                .put("assertion", oauthAssertion(provider, providerSubject, providerEmail, true)), null));
        assertThat(uuid(oauthLogin, "/user/id")).isEqualTo(adminUserId);
        assertThat(countWhere("user_auth_identities", "user_id", adminUserId)).isEqualTo(1);
        assertThat(countWhere("domain_events", "event_type", "auth.oauth_identity_linked")).isEqualTo(1);
        assertThat(countWhere("domain_events", "processing_status", "published")).isGreaterThan(0);
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "auth-" + unique + "@example.com")
                .put("username", "auth-" + unique)
                .put("displayName", "Auth Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Auth Organization")
                .put("slug", "auth-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Auth Workspace")
                .put("key", "AU" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", true));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Auth Project")
                .put("key", "AUP" + unique.substring(0, 6))
                .put("description", "Project created by auth integration test")
                .put("visibility", "public"));
        HttpResponse<String> response = post("/api/v1/setup", body, null);
        assertThat(response.statusCode()).isEqualTo(201);
        return read(response);
    }

    private AuthSession login(String identifier, String password) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", identifier)
                .put("password", password), null);
        assertThat(response.statusCode()).isEqualTo(200);
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return new AuthSession(read(response).at("/accessToken").asText(), cookie);
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

    private HttpResponse<String> getWithCookie(String path, String setCookieHeader) throws Exception {
        String cookie = setCookieHeader.substring(0, setCookieHeader.indexOf(';'));
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Cookie", cookie)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void authorize(HttpRequest.Builder builder, String accessToken) {
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
    }

    private JsonNode read(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
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

    private int countWhere(String table, String column, Object value) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private String oauthAssertion(String provider, String subject, String email, boolean emailVerified) {
        try {
            String payload = provider + "\n" + subject + "\n" + email.toLowerCase() + "\n" + emailVerified;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(OAUTH_ASSERTION_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record AuthSession(String accessToken, String cookie) {
    }
}
