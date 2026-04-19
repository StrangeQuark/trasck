package com.strangequark.trasck.agent;

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
class AgentIntegrationTest {

    private static final String CALLBACK_JWT_SECRET = "test-agent-callback-secret-that-is-long-enough";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_agent_test")
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
        registry.add("trasck.agents.callback-jwt-secret", () -> CALLBACK_JWT_SECRET);
        registry.add("trasck.events.outbox.fixed-delay-ms", () -> "600000");
    }

    @Test
    void supportsSimulatedAgentLifecycleWithSignedCallbacksReviewArtifactsAndProjections() throws Exception {
        JsonNode setup = postSetup();
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        UUID adminUserId = uuid(setup, "/adminUser/id");
        UUID memberRoleId = roleId(setup, "member");
        assertThat(roleId(setup, "agent_manager")).isNotNull();
        String accessToken = login(setup);

        JsonNode provider = read(post("/api/v1/workspaces/" + workspaceId + "/agent-providers", objectMapper.createObjectNode()
                .put("providerKey", "sim-codex")
                .put("providerType", "simulated")
                .put("displayName", "Simulated Codex")
                .put("dispatchMode", "managed"), accessToken));
        UUID providerId = uuid(provider, "/id");

        JsonNode credential = read(post("/api/v1/agent-providers/" + providerId + "/credentials", objectMapper.createObjectNode()
                .put("credentialType", "callback_signing")
                .put("secret", "raw-secret-that-must-not-be-returned")
                .set("metadata", objectMapper.createObjectNode().put("purpose", "test")), accessToken));
        assertThat(credential.toString()).doesNotContain("raw-secret-that-must-not-be-returned").doesNotContain("encryptedSecret");

        JsonNode profile = read(post("/api/v1/workspaces/" + workspaceId + "/agents", objectMapper.createObjectNode()
                .put("providerId", providerId.toString())
                .put("displayName", "Sim Agent")
                .put("username", "sim-agent")
                .put("roleId", memberRoleId.toString())
                .put("maxConcurrentTasks", 1), accessToken));
        UUID profileId = uuid(profile, "/id");
        UUID agentUserId = uuid(profile, "/userId");

        ObjectNode providerMetadata = objectMapper.createObjectNode()
                .put("owner", "strangequark")
                .put("name", "trasck")
                .put("webUrl", "https://github.com/strangequark/trasck");
        JsonNode repository = read(post("/api/v1/workspaces/" + workspaceId + "/repository-connections", objectMapper.createObjectNode()
                .put("projectId", projectId.toString())
                .put("provider", "github")
                .put("name", "trasck-github")
                .put("repositoryUrl", "https://github.com/strangequark/trasck.git")
                .put("defaultBranch", "main")
                .set("providerMetadata", providerMetadata), accessToken));
        UUID repositoryConnectionId = uuid(repository, "/id");
        assertThat(repository.at("/config/providerMetadata/owner").asText()).isEqualTo("strangequark");

        JsonNode workItem = read(post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Let an agent implement this")
                .put("reporterId", adminUserId.toString()), accessToken));
        UUID workItemId = uuid(workItem, "/id");

        ObjectNode assignRequest = objectMapper.createObjectNode()
                .put("agentProfileId", profileId.toString());
        assignRequest.set("repositoryConnectionIds", objectMapper.createArrayNode().add(repositoryConnectionId.toString()));
        assignRequest.set("requestPayload", objectMapper.createObjectNode().put("instructions", "Simulate the lifecycle."));
        JsonNode task = read(post("/api/v1/work-items/" + workItemId + "/assign-agent", assignRequest, accessToken));
        UUID taskId = uuid(task, "/id");
        String callbackToken = task.at("/callbackToken").asText();
        assertThat(task.at("/status").asText()).isEqualTo("running");
        assertThat(callbackToken).isNotBlank();
        assertThat(read(get("/api/v1/work-items/" + workItemId, accessToken)).at("/assigneeId").asText()).isEqualTo(agentUserId.toString());
        assertThat(read(get("/api/v1/agent-tasks/" + taskId, accessToken)).at("/events")).isNotEmpty();

        HttpResponse<String> invalidCallback = postCallback("/api/v1/agent-callbacks/sim-codex", objectMapper.createObjectNode()
                .put("status", "completed")
                .put("message", "bad"), "bad-token");
        assertThat(invalidCallback.statusCode()).isEqualTo(401);

        ObjectNode callback = objectMapper.createObjectNode()
                .put("status", "completed")
                .put("message", "Agent finished and requests review.");
        callback.set("resultPayload", objectMapper.createObjectNode().put("summary", "Implemented in simulation."));
        callback.set("messages", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("senderType", "agent")
                .put("bodyMarkdown", "Ready for review.")));
        callback.set("artifacts", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("artifactType", "pull_request")
                .put("name", "Simulated pull request")
                .put("externalUrl", "https://github.com/strangequark/trasck/pull/1")));
        JsonNode reviewedTask = read(postCallback("/api/v1/agent-callbacks/sim-codex", callback, callbackToken));
        assertThat(reviewedTask.at("/status").asText()).isEqualTo("review_requested");
        assertThat(reviewedTask.at("/artifacts").toString()).contains("Agent Review Request").contains("Simulated pull request");

        JsonNode comments = read(get("/api/v1/work-items/" + workItemId + "/comments", accessToken));
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).at("/authorId").asText()).isEqualTo(agentUserId.toString());
        assertThat(comments.get(0).at("/bodyMarkdown").asText()).contains("requests review");

        JsonNode replayedCallback = read(postCallback("/api/v1/agent-callbacks/sim-codex", callback, callbackToken));
        assertThat(replayedCallback.at("/status").asText()).isEqualTo("review_requested");
        assertThat(read(get("/api/v1/work-items/" + workItemId + "/comments", accessToken))).hasSize(1);

        JsonNode acceptedTask = read(post("/api/v1/agent-tasks/" + taskId + "/accept-result", objectMapper.createObjectNode(), accessToken));
        assertThat(acceptedTask.at("/status").asText()).isEqualTo("completed");

        domainEventOutboxDispatcher.dispatchPending();
        JsonNode activity = read(get("/api/v1/work-items/" + workItemId + "/activity?limit=100", accessToken));
        assertThat(eventTypes(activity)).contains("work_item.agent_assigned", "agent.task.review_requested", "work_item.comment_created");
        JsonNode audit = read(get("/api/v1/workspaces/" + workspaceId + "/audit-log?limit=100", accessToken));
        assertThat(eventTypes(audit, "action")).contains("agent.provider.created", "agent.provider.credential_created", "agent.profile.created", "agent.task.completed");
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "agent-" + unique + "@example.com")
                .put("username", "agent-" + unique)
                .put("displayName", "Agent Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Agent Organization")
                .put("slug", "agent-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Agent Workspace")
                .put("key", "AG" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", false));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Agent Project")
                .put("key", "AGP" + unique.substring(0, 6))
                .put("description", "Project created by agent integration test")
                .put("visibility", "private"));
        HttpResponse<String> response = post("/api/v1/setup", body, null);
        assertThat(response.statusCode()).isEqualTo(201);
        return read(response);
    }

    private String login(JsonNode setup) throws Exception {
        JsonNode login = read(post("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", setup.at("/adminUser/email").asText())
                .put("password", "correct-horse-battery-staple"), null));
        return login.at("/accessToken").asText();
    }

    private HttpResponse<String> post(String path, JsonNode body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postCallback(String path, JsonNode body, String assertion) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Trasck-Agent-Callback-Jwt", assertion)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

    private String[] eventTypes(JsonNode entries) {
        return eventTypes(entries, "eventType");
    }

    private String[] eventTypes(JsonNode entries, String fieldName) {
        String[] eventTypes = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            eventTypes[i] = entries.get(i).at("/" + fieldName).asText();
        }
        return eventTypes;
    }
}
