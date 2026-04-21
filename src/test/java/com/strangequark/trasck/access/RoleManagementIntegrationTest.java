package com.strangequark.trasck.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RoleManagementIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_role_management_test")
            .withUsername("trasck")
            .withPassword("trasck");

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
    }

    @Test
    void adminsCanPreviewConfirmVersionRollbackAndArchiveManagedRoles() throws Exception {
        JsonNode setup = postJson("/api/v1/setup", setupBody());
        String token = login(setup.at("/adminUser/email").asText());
        UUID workspaceId = UUID.fromString(setup.at("/workspace/id").asText());
        UUID projectId = UUID.fromString(setup.at("/project/id").asText());

        JsonNode permissions = getJson("/api/v1/workspaces/" + workspaceId + "/roles/permissions", token);
        assertThat(containsPermission(permissions, "work_item.read")).isTrue();

        JsonNode workspaceRoles = getJson("/api/v1/workspaces/" + workspaceId + "/roles", token);
        JsonNode adminRole = findRole(workspaceRoles, "workspace_owner");
        JsonNode memberRole = findRole(workspaceRoles, "member");
        assertThat(adminRole.path("permissionKeys")).isNotEmpty();
        assertThat(memberRole.path("permissionKeys")).isNotEmpty();

        ArrayNode unsafeAdminPermissions = permissionArrayWithout(adminRole.path("permissionKeys"), "workspace.admin");
        JsonNode unsafePreview = postJson(
                "/api/v1/workspaces/" + workspaceId + "/roles/" + adminRole.path("id").asText() + "/permission-preview",
                objectMapper.createObjectNode().set("permissionKeys", unsafeAdminPermissions),
                token
        );
        assertThat(unsafePreview.path("removesAdministrativePermission").asBoolean()).isTrue();
        HttpResponse<String> unsafeUpdate = put(
                "/api/v1/workspaces/" + workspaceId + "/roles/" + adminRole.path("id").asText() + "/permissions",
                permissionUpdateBody(unsafeAdminPermissions, true, unsafePreview.path("previewToken").asText()),
                token
        );
        assertThat(unsafeUpdate.statusCode()).isEqualTo(409);

        ArrayNode reducedMemberPermissions = permissionArrayWithout(memberRole.path("permissionKeys"), "work_item.update");
        JsonNode memberPreview = postJson(
                "/api/v1/workspaces/" + workspaceId + "/roles/" + memberRole.path("id").asText() + "/permission-preview",
                objectMapper.createObjectNode().set("permissionKeys", reducedMemberPermissions),
                token
        );
        assertThat(memberPreview.path("removedPermissionKeys").toString()).contains("work_item.update");
        HttpResponse<String> unconfirmed = put(
                "/api/v1/workspaces/" + workspaceId + "/roles/" + memberRole.path("id").asText() + "/permissions",
                permissionUpdateBody(reducedMemberPermissions, false, memberPreview.path("previewToken").asText()),
                token
        );
        assertThat(unconfirmed.statusCode()).isEqualTo(409);

        JsonNode updatedMember = putJson(
                "/api/v1/workspaces/" + workspaceId + "/roles/" + memberRole.path("id").asText() + "/permissions",
                permissionUpdateBody(reducedMemberPermissions, true, memberPreview.path("previewToken").asText()),
                token
        );
        assertThat(updatedMember.path("permissionKeys").toString()).doesNotContain("work_item.update");

        JsonNode memberVersions = getJson("/api/v1/workspaces/" + workspaceId + "/roles/" + memberRole.path("id").asText() + "/versions", token);
        assertThat(memberVersions).hasSizeGreaterThanOrEqualTo(2);
        JsonNode baseline = findVersion(memberVersions, "baseline");
        JsonNode rolledBack = postJson(
                "/api/v1/workspaces/" + workspaceId + "/roles/" + memberRole.path("id").asText() + "/versions/" + baseline.path("id").asText() + "/rollback",
                objectMapper.createObjectNode(),
                token
        );
        assertThat(rolledBack.path("permissionKeys").toString()).contains("work_item.update");

        ObjectNode customRoleBody = objectMapper.createObjectNode()
                .put("key", "qa_observer")
                .put("name", "QA Observer")
                .put("description", "Reads work for QA review");
        customRoleBody.set("permissionKeys", permissionArray("workspace.read", "work_item.read"));
        JsonNode customRole = postJson("/api/v1/workspaces/" + workspaceId + "/roles", customRoleBody, token);
        assertThat(customRole.path("systemRole").asBoolean()).isFalse();
        assertThat(post("/api/v1/workspaces/" + workspaceId + "/roles", customRoleBody, token).statusCode()).isEqualTo(409);
        assertThat(delete("/api/v1/workspaces/" + workspaceId + "/roles/" + customRole.path("id").asText(), token).statusCode()).isEqualTo(204);

        JsonNode projectRole = postJson(
                "/api/v1/projects/" + projectId + "/roles",
                objectMapper.createObjectNode()
                        .put("key", "project_observer")
                        .put("name", "Project Observer")
                        .put("description", "Reads project work")
                        .set("permissionKeys", permissionArray("project.read", "work_item.read")),
                token
        );
        assertThat(projectRole.path("scope").asText()).isEqualTo("project");
        JsonNode projectRoles = getJson("/api/v1/projects/" + projectId + "/roles", token);
        assertThat(findRole(projectRoles, "project_observer").path("name").asText()).isEqualTo("Project Observer");
    }

    private boolean containsPermission(JsonNode permissions, String key) {
        for (JsonNode permission : permissions) {
            if (key.equals(permission.path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode findRole(JsonNode roles, String key) {
        for (JsonNode role : roles) {
            if (key.equals(role.path("key").asText())) {
                return role;
            }
        }
        throw new AssertionError("Missing role " + key + " in " + roles);
    }

    private JsonNode findVersion(JsonNode versions, String changeType) {
        for (JsonNode version : versions) {
            if (changeType.equals(version.path("changeType").asText())) {
                return version;
            }
        }
        throw new AssertionError("Missing role version " + changeType + " in " + versions);
    }

    private ArrayNode permissionArray(String... keys) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String key : keys) {
            array.add(key);
        }
        return array;
    }

    private ArrayNode permissionArrayWithout(JsonNode keys, String omitted) {
        ArrayNode array = objectMapper.createArrayNode();
        for (JsonNode key : keys) {
            if (!omitted.equals(key.asText())) {
                array.add(key.asText());
            }
        }
        return array;
    }

    private ObjectNode permissionUpdateBody(ArrayNode permissionKeys, boolean confirmed, String previewToken) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("permissionKeys", permissionKeys);
        body.put("confirmed", confirmed);
        body.put("previewToken", previewToken);
        return body;
    }

    private JsonNode setupBody() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "roles-" + unique + "@example.com")
                .put("username", "roles-" + unique)
                .put("displayName", "Role Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Role Organization")
                .put("slug", "role-organization-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Role Workspace")
                .put("key", "RW" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", false));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Role Project")
                .put("key", "RP" + unique.substring(0, 6))
                .put("description", "Project created by role management integration test")
                .put("visibility", "private"));
        return body;
    }

    private String login(String email) throws Exception {
        JsonNode login = postJson("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", email)
                .put("password", "correct-horse-battery-staple"));
        return login.at("/accessToken").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        HttpResponse<String> response = get(path, token);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private JsonNode postJson(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = post(path, body, null);
        assertThat(response.statusCode()).isIn(200, 201);
        return objectMapper.readTree(response.body());
    }

    private JsonNode postJson(String path, JsonNode body, String token) throws Exception {
        HttpResponse<String> response = post(path, body, token);
        assertThat(response.statusCode()).isIn(200, 201);
        return objectMapper.readTree(response.body());
    }

    private JsonNode putJson(String path, JsonNode body, String token) throws Exception {
        HttpResponse<String> response = put(path, body, token);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, JsonNode body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, JsonNode body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
