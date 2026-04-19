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
        registry.add("trasck.events.outbox.fixed-delay-ms", () -> "600000");
    }

    @Test
    void supportsSimulatedAgentLifecycleWithSignedCallbacksReviewArtifactsAndProjections() throws Exception {
        JsonNode setup = postSetup();
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        UUID adminUserId = uuid(setup, "/adminUser/id");
        UUID memberRoleId = roleId(setup, "member");
        UUID viewerRoleId = roleId(setup, "viewer");
        assertThat(roleId(setup, "agent_manager")).isNotNull();
        String accessToken = login(setup);

        JsonNode provider = read(post("/api/v1/workspaces/" + workspaceId + "/agent-providers", objectMapper.createObjectNode()
                .put("providerKey", "sim-codex")
                .put("providerType", "simulated")
                .put("displayName", "Simulated Codex")
                .put("dispatchMode", "managed"), accessToken));
        UUID providerId = uuid(provider, "/id");
        assertThat(provider.at("/config/callbackJwt/algorithm").asText()).isEqualTo("RS256");
        assertThat(provider.at("/config/callbackJwt/keys/0/privateKeyPem").isMissingNode()).isTrue();
        assertThat(provider.at("/config/callbackJwt/keys/0/publicJwk/kty").asText()).isEqualTo("RSA");
        assertThat(provider.toString()).doesNotContain("BEGIN PRIVATE KEY");

        JsonNode codexProvider = read(post("/api/v1/workspaces/" + workspaceId + "/agent-providers", objectMapper.createObjectNode()
                .put("providerKey", "codex-main")
                .put("providerType", "codex")
                .put("displayName", "Codex")
                .put("dispatchMode", "managed"), accessToken));
        assertThat(codexProvider.at("/providerType").asText()).isEqualTo("codex");
        JsonNode claudeProvider = read(post("/api/v1/workspaces/" + workspaceId + "/agent-providers", objectMapper.createObjectNode()
                .put("providerKey", "claude-main")
                .put("providerType", "claude_code")
                .put("displayName", "Claude Code")
                .put("dispatchMode", "managed"), accessToken));
        assertThat(claudeProvider.at("/providerType").asText()).isEqualTo("claude_code");
        JsonNode workerProvider = read(post("/api/v1/workspaces/" + workspaceId + "/agent-providers", objectMapper.createObjectNode()
                .put("providerKey", "worker-main")
                .put("providerType", "generic_worker")
                .put("displayName", "Generic Worker")
                .put("dispatchMode", "polling"), accessToken));
        UUID workerProviderId = uuid(workerProvider, "/id");
        assertThat(workerProvider.at("/providerType").asText()).isEqualTo("generic_worker");
        assertThat(workerProvider.at("/config/callbackJwt/keys/0/privateKeyPem").isMissingNode()).isTrue();

        JsonNode credential = read(post("/api/v1/agent-providers/" + providerId + "/credentials", objectMapper.createObjectNode()
                .put("credentialType", "callback_signing")
                .put("secret", "raw-secret-that-must-not-be-returned")
                .set("metadata", objectMapper.createObjectNode().put("purpose", "test")), accessToken));
        assertThat(credential.toString()).doesNotContain("raw-secret-that-must-not-be-returned").doesNotContain("encryptedSecret");
        JsonNode workerCredential = read(post("/api/v1/agent-providers/" + workerProviderId + "/credentials", objectMapper.createObjectNode()
                .put("credentialType", "worker_token")
                .put("secret", "worker-secret")
                .set("metadata", objectMapper.createObjectNode().put("purpose", "worker-auth")), accessToken));
        assertThat(workerCredential.toString()).doesNotContain("worker-secret").doesNotContain("encryptedSecret");
        JsonNode temporaryCredential = read(post("/api/v1/agent-providers/" + workerProviderId + "/credentials", objectMapper.createObjectNode()
                .put("credentialType", "temporary_api_token")
                .put("secret", "temporary-secret"), accessToken));
        assertThat(read(get("/api/v1/agent-providers/" + workerProviderId + "/credentials", accessToken))).hasSizeGreaterThanOrEqualTo(3);
        assertThat(read(post("/api/v1/agent-providers/" + workerProviderId + "/credentials/reencrypt", objectMapper.createObjectNode(), accessToken))).hasSizeGreaterThanOrEqualTo(3);
        assertThat(read(post("/api/v1/agent-providers/" + workerProviderId + "/credentials/" + uuid(temporaryCredential, "/id") + "/deactivate", objectMapper.createObjectNode(), accessToken))
                .at("/active").asBoolean()).isFalse();
        JsonNode rotatedWorkerProvider = read(post("/api/v1/agent-providers/" + workerProviderId + "/callback-keys/rotate", objectMapper.createObjectNode(), accessToken));
        assertThat(rotatedWorkerProvider.at("/config/callbackJwt/keys")).hasSize(2);
        assertThat(rotatedWorkerProvider.at("/config/callbackJwt/keys/1/privateKeyPem").isMissingNode()).isTrue();

        ObjectNode profileRequest = objectMapper.createObjectNode()
                .put("providerId", providerId.toString())
                .put("displayName", "Sim Agent")
                .put("username", "sim-agent")
                .put("roleId", memberRoleId.toString())
                .put("maxConcurrentTasks", 1);
        profileRequest.set("projectIds", objectMapper.createArrayNode().add(projectId.toString()));
        JsonNode profile = read(post("/api/v1/workspaces/" + workspaceId + "/agents", profileRequest, accessToken));
        UUID profileId = uuid(profile, "/id");
        UUID agentUserId = uuid(profile, "/userId");
        assertThat(profile.at("/projectIds")).hasSize(1);

        ObjectNode workerProfileRequest = objectMapper.createObjectNode()
                .put("providerId", workerProviderId.toString())
                .put("displayName", "Worker Agent")
                .put("username", "worker-agent")
                .put("roleId", memberRoleId.toString())
                .put("maxConcurrentTasks", 1);
        workerProfileRequest.set("projectIds", objectMapper.createArrayNode().add(projectId.toString()));
        JsonNode workerProfile = read(post("/api/v1/workspaces/" + workspaceId + "/agents", workerProfileRequest, accessToken));
        UUID workerProfileId = uuid(workerProfile, "/id");

        ObjectNode viewerProfileRequest = objectMapper.createObjectNode()
                .put("providerId", providerId.toString())
                .put("displayName", "Read Only Agent")
                .put("username", "read-only-agent")
                .put("roleId", viewerRoleId.toString())
                .put("maxConcurrentTasks", 1);
        viewerProfileRequest.set("projectIds", objectMapper.createArrayNode().add(projectId.toString()));
        JsonNode viewerProfile = read(post("/api/v1/workspaces/" + workspaceId + "/agents", viewerProfileRequest, accessToken));
        UUID viewerProfileId = uuid(viewerProfile, "/id");

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
        JsonNode workerWorkItem = read(post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Let a generic worker implement this")
                .put("reporterId", adminUserId.toString()), accessToken));
        UUID workerWorkItemId = uuid(workerWorkItem, "/id");
        JsonNode restrictedWorkItem = read(post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Read-only agent cannot comment here")
                .put("reporterId", adminUserId.toString()), accessToken));
        UUID restrictedWorkItemId = uuid(restrictedWorkItem, "/id");
        read(post("/api/v1/work-items/" + workItemId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "open_to_ready"), accessToken));
        read(post("/api/v1/work-items/" + workItemId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "ready_to_in_progress"), accessToken));
        JsonNode reviewWorkItem = read(post("/api/v1/work-items/" + workItemId + "/transition", objectMapper.createObjectNode()
                .put("transitionKey", "in_progress_to_in_review"), accessToken));
        assertThat(uuid(reviewWorkItem, "/statusId")).isEqualTo(statusId(setup, "in_review"));

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

        ObjectNode workerAssignRequest = objectMapper.createObjectNode()
                .put("agentProfileId", workerProfileId.toString());
        workerAssignRequest.set("repositoryConnectionIds", objectMapper.createArrayNode().add(repositoryConnectionId.toString()));
        workerAssignRequest.set("requestPayload", objectMapper.createObjectNode().put("instructions", "Use the generic worker protocol."));
        JsonNode workerTask = read(post("/api/v1/work-items/" + workerWorkItemId + "/assign-agent", workerAssignRequest, accessToken));
        UUID workerTaskId = uuid(workerTask, "/id");
        assertThat(workerTask.at("/status").asText()).isEqualTo("running");
        JsonNode pushDispatch = read(post("/api/v1/agent-tasks/" + workerTaskId + "/worker-dispatch", objectMapper.createObjectNode(), accessToken));
        assertThat(pushDispatch.at("/protocolVersion").asText()).isEqualTo("trasck.worker.v1");
        assertThat(pushDispatch.at("/transport").asText()).isEqualTo("webhook_push");
        assertThat(pushDispatch.at("/callbackToken").asText()).isNotBlank();
        assertThat(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/claim", objectMapper.createObjectNode()
                .put("workerId", "worker-1"), "bad-secret").statusCode()).isEqualTo(401);
        JsonNode claimedTask = read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/claim", objectMapper.createObjectNode()
                .put("workerId", "worker-1"), "worker-secret"));
        assertThat(uuid(claimedTask, "/taskId")).isEqualTo(workerTaskId);
        assertThat(claimedTask.at("/transport").asText()).isEqualTo("polling");
        assertThat(claimedTask.at("/endpoints/callback").asText()).isEqualTo("/api/v1/agent-callbacks/worker_main");
        JsonNode heartbeat = read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/" + workerTaskId + "/heartbeat", objectMapper.createObjectNode()
                .put("workerId", "worker-1")
                .put("status", "waiting_for_input")
                .put("message", "Need clarification."), "worker-secret"));
        assertThat(heartbeat.at("/status").asText()).isEqualTo("waiting_for_input");
        JsonNode humanMessage = read(post("/api/v1/agent-tasks/" + workerTaskId + "/messages", objectMapper.createObjectNode()
                .put("bodyMarkdown", "Use the default implementation path."), accessToken));
        assertThat(humanMessage.at("/status").asText()).isEqualTo("running");
        JsonNode changesRequested = read(post("/api/v1/agent-tasks/" + workerTaskId + "/request-changes", objectMapper.createObjectNode()
                .put("message", "Please add tests before review."), accessToken));
        assertThat(changesRequested.at("/status").asText()).isEqualTo("waiting_for_input");
        read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/" + workerTaskId + "/logs", objectMapper.createObjectNode()
                .put("workerId", "worker-1")
                .put("eventType", "worker_progress")
                .put("message", "Tests are running."), "worker-secret"));
        read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/" + workerTaskId + "/messages", objectMapper.createObjectNode()
                .put("senderType", "agent")
                .put("bodyMarkdown", "I added the tests."), "worker-secret"));
        JsonNode artifactTask = read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/" + workerTaskId + "/artifacts", objectMapper.createObjectNode()
                .put("artifactType", "branch")
                .put("name", "worker/implementation")
                .put("externalUrl", "https://example.com/worker/implementation"), "worker-secret"));
        assertThat(artifactTask.at("/artifacts").toString()).contains("worker/implementation");
        JsonNode canceledWorkerTask = read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/" + workerTaskId + "/cancel", objectMapper.createObjectNode()
                .put("workerId", "worker-1")
                .put("message", "Cancel acknowledged."), "worker-secret"));
        assertThat(canceledWorkerTask.at("/status").asText()).isEqualTo("canceled");
        JsonNode retriedWorkerTask = read(postWorker("/api/v1/workspaces/" + workspaceId + "/agent-workers/worker-main/tasks/" + workerTaskId + "/retry", objectMapper.createObjectNode()
                .put("workerId", "worker-1")
                .put("message", "Retry started."), "worker-secret"));
        assertThat(retriedWorkerTask.at("/status").asText()).isEqualTo("running");
        String workerCallbackToken = retriedWorkerTask.at("/callbackToken").asText();

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

        JsonNode workerReviewedTask = read(postCallback("/api/v1/agent-callbacks/worker-main", callback, workerCallbackToken));
        assertThat(workerReviewedTask.at("/status").asText()).isEqualTo("review_requested");
        assertThat(workerReviewedTask.at("/artifacts").toString()).contains("worker/implementation", "Simulated pull request");

        ObjectNode restrictedAssignRequest = objectMapper.createObjectNode()
                .put("agentProfileId", viewerProfileId.toString());
        JsonNode restrictedTask = read(post("/api/v1/work-items/" + restrictedWorkItemId + "/assign-agent", restrictedAssignRequest, accessToken));
        HttpResponse<String> restrictedCallback = postCallback("/api/v1/agent-callbacks/sim-codex", callback, restrictedTask.at("/callbackToken").asText());
        assertThat(restrictedCallback.statusCode()).isEqualTo(403);

        JsonNode acceptedTask = read(post("/api/v1/agent-tasks/" + taskId + "/accept-result", objectMapper.createObjectNode(), accessToken));
        assertThat(acceptedTask.at("/status").asText()).isEqualTo("completed");
        assertThat(uuid(read(get("/api/v1/work-items/" + workItemId, accessToken)), "/statusId")).isEqualTo(statusId(setup, "approval"));

        domainEventOutboxDispatcher.dispatchPending();
        JsonNode activity = read(get("/api/v1/work-items/" + workItemId + "/activity?limit=100", accessToken));
        assertThat(eventTypes(activity)).contains("work_item.agent_assigned", "agent.task.review_requested", "work_item.comment_created", "work_item.agent_acceptance_transitioned");
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

    private HttpResponse<String> postWorker(String path, JsonNode body, String workerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Trasck-Worker-Token", workerToken)
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

    private UUID statusId(JsonNode setup, String key) {
        for (JsonNode status : setup.at("/seedData/workflow/statuses")) {
            if (key.equals(status.at("/key").asText())) {
                return UUID.fromString(status.at("/id").asText());
            }
        }
        throw new IllegalStateException("Status not found: " + key);
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
