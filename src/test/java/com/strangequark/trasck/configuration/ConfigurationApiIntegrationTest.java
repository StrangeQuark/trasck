package com.strangequark.trasck.configuration;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConfigurationApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_configuration_test")
            .withUsername("trasck")
            .withPassword("trasck");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Test
    void managesConfigurationPlanningAutomationNotificationAndImportApis() throws Exception {
        JsonNode setup = postSetup();
        UUID actorId = uuid(setup, "/adminUser/id");
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        accessToken = login(setup);

        JsonNode story = createWorkItem(projectId, actorId, "story", "Configurable delivery story");
        UUID storyId = uuid(story, "/id");
        UUID storyTypeId = uuid(story, "/typeId");

        JsonNode customerField = postJson("/api/v1/workspaces/" + workspaceId + "/custom-fields", objectMapper.createObjectNode()
                .put("name", "Customer Tier")
                .put("key", "customer-tier")
                .put("fieldType", "single_select")
                .put("searchable", true));
        UUID customerFieldId = uuid(customerField, "/id");
        ObjectNode fieldConfigBody = objectMapper.createObjectNode()
                .put("customFieldId", customerFieldId.toString())
                .put("projectId", projectId.toString())
                .put("workItemTypeId", storyTypeId.toString())
                .put("required", true)
                .put("hidden", false)
                .put("defaultValue", "gold");
        fieldConfigBody.set("validationConfig", objectMapper.createObjectNode().put("allowEmpty", false));
        JsonNode fieldConfiguration = postJson("/api/v1/workspaces/" + workspaceId + "/field-configurations", fieldConfigBody);
        UUID fieldConfigurationId = uuid(fieldConfiguration, "/id");
        assertThat(fieldConfiguration.at("/required").asBoolean()).isTrue();
        JsonNode updatedFieldConfiguration = patch("/api/v1/field-configurations/" + fieldConfigurationId, objectMapper.createObjectNode()
                .put("required", false));
        assertThat(updatedFieldConfiguration.at("/required").asBoolean()).isFalse();
        assertThat(getJson("/api/v1/custom-fields/" + customerFieldId + "/field-configurations")).hasSize(1);

        ObjectNode boardBody = objectMapper.createObjectNode()
                .put("name", "Delivery Board")
                .put("type", "kanban")
                .put("active", true);
        boardBody.set("filterConfig", objectMapper.createObjectNode().put("query", "project = TRK"));
        JsonNode board = postJson("/api/v1/projects/" + projectId + "/boards", boardBody);
        UUID boardId = uuid(board, "/id");
        ObjectNode columnBody = objectMapper.createObjectNode()
                .put("name", "Done")
                .put("position", 1)
                .put("doneColumn", true);
        columnBody.set("statusIds", objectMapper.createArrayNode().add(statusId(setup, "done").toString()));
        JsonNode column = postJson("/api/v1/boards/" + boardId + "/columns", columnBody);
        assertThat(column.at("/doneColumn").asBoolean()).isTrue();
        ObjectNode swimlaneBody = objectMapper.createObjectNode()
                .put("name", "By assignee")
                .put("swimlaneType", "assignee")
                .put("position", 0)
                .put("enabled", true);
        swimlaneBody.set("query", objectMapper.createObjectNode());
        JsonNode swimlane = postJson("/api/v1/boards/" + boardId + "/swimlanes", swimlaneBody);
        assertThat(swimlane.at("/swimlaneType").asText()).isEqualTo("assignee");
        assertThat(getJson("/api/v1/projects/" + projectId + "/boards")).hasSizeGreaterThanOrEqualTo(2);
        JsonNode boardWork = getJson("/api/v1/boards/" + boardId + "/work-items");
        assertThat(boardWork.at("/columns")).hasSize(1);

        JsonNode release = postJson("/api/v1/projects/" + projectId + "/releases", objectMapper.createObjectNode()
                .put("name", "Release 1")
                .put("version", "1.0.0")
                .put("startDate", "2026-04-20")
                .put("releaseDate", "2026-05-01")
                .put("status", "planned")
                .put("description", "First release"));
        UUID releaseId = uuid(release, "/id");
        JsonNode releaseWorkItem = postJson("/api/v1/releases/" + releaseId + "/work-items", objectMapper.createObjectNode()
                .put("workItemId", storyId.toString()));
        assertThat(uuid(releaseWorkItem, "/workItemId")).isEqualTo(storyId);

        ObjectNode roadmapBody = objectMapper.createObjectNode()
                .put("projectId", projectId.toString())
                .put("name", "Delivery Roadmap")
                .put("visibility", "workspace");
        roadmapBody.set("config", objectMapper.createObjectNode().put("groupBy", "type"));
        JsonNode roadmap = postJson("/api/v1/workspaces/" + workspaceId + "/roadmaps", roadmapBody);
        UUID roadmapId = uuid(roadmap, "/id");
        ObjectNode roadmapItemBody = objectMapper.createObjectNode()
                .put("workItemId", storyId.toString())
                .put("startDate", "2026-04-20")
                .put("endDate", "2026-05-01")
                .put("position", 0);
        roadmapItemBody.set("displayConfig", objectMapper.createObjectNode().put("color", "green"));
        JsonNode roadmapItem = postJson("/api/v1/roadmaps/" + roadmapId + "/items", roadmapItemBody);
        assertThat(uuid(roadmapItem, "/workItemId")).isEqualTo(storyId);

        JsonNode preference = postJson("/api/v1/workspaces/" + workspaceId + "/notification-preferences", objectMapper.createObjectNode()
                .put("channel", "in_app")
                .put("eventType", "automation.rule_executed")
                .put("enabled", true));
        assertThat(preference.at("/enabled").asBoolean()).isTrue();
        JsonNode defaultPreference = postJson("/api/v1/workspaces/" + workspaceId + "/notification-defaults", objectMapper.createObjectNode()
                .put("channel", "email")
                .put("eventType", "automation.failure")
                .put("enabled", false));
        assertThat(defaultPreference.at("/userId").isMissingNode() || defaultPreference.at("/userId").isNull()).isTrue();
        JsonNode updatedDefaultPreference = postJson("/api/v1/workspaces/" + workspaceId + "/notification-defaults", objectMapper.createObjectNode()
                .put("channel", "email")
                .put("eventType", "automation.failure")
                .put("enabled", true));
        assertThat(uuid(updatedDefaultPreference, "/id")).isEqualTo(uuid(defaultPreference, "/id"));
        assertThat(updatedDefaultPreference.at("/enabled").asBoolean()).isTrue();
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/notification-defaults")).hasSize(1);

        ObjectNode webhookBody = objectMapper.createObjectNode()
                .put("name", "Automation Webhook")
                .put("url", "https://example.com/trasck")
                .put("secret", "development-secret")
                .put("enabled", true);
        webhookBody.set("eventTypes", objectMapper.createArrayNode().add("automation.test"));
        JsonNode webhook = postJson("/api/v1/workspaces/" + workspaceId + "/webhooks", webhookBody);
        UUID webhookId = uuid(webhook, "/id");
        assertThat(webhook.at("/secretConfigured").asBoolean()).isTrue();

        ObjectNode ruleBody = objectMapper.createObjectNode()
                .put("name", "Notify and webhook")
                .put("triggerType", "manual")
                .put("enabled", true);
        ruleBody.set("triggerConfig", objectMapper.createObjectNode());
        JsonNode rule = postJson("/api/v1/workspaces/" + workspaceId + "/automation-rules", ruleBody);
        UUID ruleId = uuid(rule, "/id");
        ObjectNode notificationConfig = objectMapper.createObjectNode()
                .put("userId", actorId.toString())
                .put("title", "Automation completed")
                .put("body", "The rule executed.")
                .put("targetType", "work_item")
                .put("targetId", storyId.toString());
        ObjectNode notificationAction = objectMapper.createObjectNode()
                .put("actionType", "create_notification")
                .put("executionMode", "sync")
                .put("position", 0);
        notificationAction.set("config", notificationConfig);
        postJson("/api/v1/automation-rules/" + ruleId + "/actions", notificationAction);
        ObjectNode webhookConfig = objectMapper.createObjectNode()
                .put("webhookId", webhookId.toString())
                .put("eventType", "automation.test");
        ObjectNode webhookAction = objectMapper.createObjectNode()
                .put("actionType", "webhook")
                .put("executionMode", "async")
                .put("position", 1);
        webhookAction.set("config", webhookConfig);
        postJson("/api/v1/automation-rules/" + ruleId + "/actions", webhookAction);
        ObjectNode emailConfig = objectMapper.createObjectNode()
                .put("userId", actorId.toString())
                .put("subject", "Automation email")
                .put("body", "Maildev development delivery");
        ObjectNode emailAction = objectMapper.createObjectNode()
                .put("actionType", "email")
                .put("executionMode", "async")
                .put("position", 2);
        emailAction.set("config", emailConfig);
        postJson("/api/v1/automation-rules/" + ruleId + "/actions", emailAction);
        ObjectNode executionBody = objectMapper.createObjectNode()
                .put("sourceEntityType", "work_item")
                .put("sourceEntityId", storyId.toString());
        executionBody.set("payload", objectMapper.createObjectNode().put("workItemId", storyId.toString()));
        JsonNode execution = postJson("/api/v1/automation-rules/" + ruleId + "/execute", executionBody);
        assertThat(execution.at("/status").asText()).isEqualTo("queued");
        JsonNode workerRun = postJson("/api/v1/workspaces/" + workspaceId + "/automation-jobs/run-queued", objectMapper.createObjectNode()
                .put("limit", 5));
        assertThat(workerRun.at("/processed").asInt()).isEqualTo(1);
        assertThat(workerRun.at("/succeeded").asInt()).isEqualTo(1);
        assertThat(workerRun.at("/jobs/0/logs")).hasSize(3);
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/notifications")).hasSize(1);
        JsonNode deliveries = getJson("/api/v1/webhooks/" + webhookId + "/deliveries");
        assertThat(deliveries).hasSize(1);
        UUID webhookDeliveryId = UUID.fromString(deliveries.at("/0/id").asText());
        assertThat(getJson("/api/v1/webhook-deliveries/" + webhookDeliveryId).at("/status").asText()).isEqualTo("queued");
        assertThat(postJson("/api/v1/webhook-deliveries/" + webhookDeliveryId + "/cancel", objectMapper.createObjectNode()).at("/status").asText()).isEqualTo("cancelled");
        assertThat(postJson("/api/v1/webhook-deliveries/" + webhookDeliveryId + "/retry", objectMapper.createObjectNode()).at("/status").asText()).isEqualTo("queued");
        JsonNode webhookWorkerRun = postJson("/api/v1/workspaces/" + workspaceId + "/webhook-deliveries/process", objectMapper.createObjectNode()
                .put("dryRun", true)
                .put("maxAttempts", 2));
        assertThat(webhookWorkerRun.at("/delivered").asInt()).isEqualTo(1);
        JsonNode emails = getJson("/api/v1/workspaces/" + workspaceId + "/email-deliveries");
        assertThat(emails).hasSize(1);
        UUID emailDeliveryId = UUID.fromString(emails.at("/0/id").asText());
        assertThat(postJson("/api/v1/email-deliveries/" + emailDeliveryId + "/cancel", objectMapper.createObjectNode()).at("/status").asText()).isEqualTo("cancelled");
        assertThat(postJson("/api/v1/email-deliveries/" + emailDeliveryId + "/retry", objectMapper.createObjectNode()).at("/status").asText()).isEqualTo("queued");
        JsonNode emailWorkerRun = postJson("/api/v1/workspaces/" + workspaceId + "/email-deliveries/process", objectMapper.createObjectNode()
                .put("dryRun", true)
                .put("maxAttempts", 2));
        assertThat(emailWorkerRun.at("/sent").asInt()).isEqualTo(1);
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-health")).hasSizeGreaterThanOrEqualTo(3);
        JsonNode workerSettings = patch("/api/v1/workspaces/" + workspaceId + "/automation-worker-settings", objectMapper.createObjectNode()
                .put("automationJobsEnabled", true)
                .put("webhookDeliveriesEnabled", true)
                .put("emailDeliveriesEnabled", true)
                .put("automationLimit", 5)
                .put("webhookLimit", 5)
                .put("emailLimit", 5)
                .put("webhookDryRun", true)
                .put("emailDryRun", true));
        assertThat(workerSettings.at("/automationJobsEnabled").asBoolean()).isTrue();

        JsonNode importJob = postJson("/api/v1/workspaces/" + workspaceId + "/import-jobs", objectMapper.createObjectNode()
                .put("provider", "jira"));
        UUID importJobId = uuid(importJob, "/id");
        JsonNode importRecord = postJson("/api/v1/import-jobs/" + importJobId + "/records", objectMapper.createObjectNode()
                .put("sourceType", "issue")
                .put("sourceId", "JIRA-1")
                .put("targetType", "work_item")
                .put("targetId", storyId.toString())
                .put("status", "imported"));
        assertThat(importRecord.at("/sourceId").asText()).isEqualTo("JIRA-1");
        ObjectNode parseBody = objectMapper.createObjectNode()
                .put("content", """
                        {"issues":[{"key":"JIRA-2","fields":{"summary":"  Parsed story  ","issuetype":{"name":"Story"},"status":{"name":"To Do"},"security":"Public"}}]}
                        """);
        JsonNode parsedImport = postJson("/api/v1/import-jobs/" + importJobId + "/parse", parseBody);
        assertThat(parsedImport.at("/recordsParsed").asInt()).isEqualTo(1);
        assertThat(parsedImport.at("/records/0/sourceId").asText()).isEqualTo("JIRA-2");
        ObjectNode mappingBody = objectMapper.createObjectNode()
                .put("name", "Jira story mapping")
                .put("provider", "jira")
                .put("sourceType", "issue")
                .put("targetType", "work_item")
                .put("projectId", projectId.toString())
                .put("enabled", true);
        mappingBody.set("fieldMapping", objectMapper.createObjectNode()
                .put("title", "fields.summary")
                .put("typeKey", "fields.issuetype.name")
                .put("statusKey", "fields.status.name")
                .put("descriptionMarkdown", "fields.description"));
        mappingBody.set("defaults", objectMapper.createObjectNode()
                .put("descriptionMarkdown", "Imported Jira issue"));
        ObjectNode transformationConfig = objectMapper.createObjectNode();
        transformationConfig.set("title", objectMapper.createArrayNode().add("trim"));
        mappingBody.set("transformationConfig", transformationConfig);
        JsonNode mapping = postJson("/api/v1/workspaces/" + workspaceId + "/import-mapping-templates", mappingBody);
        postJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/type-translations", objectMapper.createObjectNode()
                .put("sourceTypeKey", "Story")
                .put("targetTypeKey", "story")
                .put("enabled", true));
        postJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/status-translations", objectMapper.createObjectNode()
                .put("sourceStatusKey", "To Do")
                .put("targetStatusKey", "open")
                .put("enabled", true));
        postJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/value-lookups", objectMapper.createObjectNode()
                .put("sourceField", "fields.security")
                .put("sourceValue", "Public")
                .put("targetField", "visibility")
                .put("targetValue", "public")
                .put("enabled", true));
        assertThat(getJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/type-translations")).hasSize(1);
        assertThat(getJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/status-translations")).hasSize(1);
        assertThat(getJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/value-lookups")).hasSize(1);
        JsonNode materialized = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        assertThat(materialized.at("/created").asInt()).isEqualTo(1);
        UUID importedWorkItemId = uuid(materialized, "/records/0/targetId");
        JsonNode importedWorkItem = getJson("/api/v1/work-items/" + importedWorkItemId);
        assertThat(importedWorkItem.at("/title").asText()).isEqualTo("Parsed story");
        assertThat(importedWorkItem.at("/visibility").asText()).isEqualTo("public");
        JsonNode completedImportJob = postJson("/api/v1/import-jobs/" + importJobId + "/complete", objectMapper.createObjectNode());
        assertThat(completedImportJob.at("/status").asText()).isEqualTo("completed");
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "configuration-" + unique + "@example.com")
                .put("username", "configuration-" + unique)
                .put("displayName", "Configuration Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Configuration Organization")
                .put("slug", "configuration-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Configuration Workspace")
                .put("key", "CF" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", true));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Configuration Project")
                .put("key", "CFG" + unique.substring(0, 6))
                .put("description", "Project created by configuration integration test")
                .put("visibility", "public"));
        HttpResponse<String> response = rawPost("/api/v1/setup", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private String login(JsonNode setup) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("identifier", setup.at("/adminUser/email").asText())
                .put("password", "correct-horse-battery-staple");
        HttpResponse<String> response = rawPost("/api/v1/auth/login", body);
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).at("/accessToken").asText();
    }

    private JsonNode createWorkItem(UUID projectId, UUID actorId, String typeKey, String title) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("typeKey", typeKey)
                .put("title", title)
                .put("descriptionMarkdown", "Test work item")
                .put("reporterId", actorId.toString());
        body.set("descriptionDocument", objectMapper.createObjectNode()
                .put("type", "doc")
                .put("title", title));
        HttpResponse<String> response = post("/api/v1/projects/" + projectId + "/work-items", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private JsonNode postJson(String path, JsonNode body) throws Exception {
        HttpResponse<String> response = post(path, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
    }

    private JsonNode patch(String path, JsonNode body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readTree(response.body());
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

    private HttpResponse<String> rawPost(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
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

    private UUID statusId(JsonNode setup, String key) {
        for (JsonNode status : setup.at("/seedData/workflow/statuses")) {
            if (key.equals(status.at("/key").asText())) {
                return UUID.fromString(status.at("/id").asText());
            }
        }
        throw new IllegalStateException("Status not found: " + key);
    }
}
