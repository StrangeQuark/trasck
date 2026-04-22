package com.strangequark.trasck.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("trasck.webhooks.previous-secret-overlap", () -> "PT2H");
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
        JsonNode boardPeerStory = createWorkItem(projectId, actorId, "story", "Board peer story");
        UUID boardPeerStoryId = uuid(boardPeerStory, "/id");
        JsonNode openBoardStory = createWorkItem(projectId, actorId, "story", "Open board story");
        UUID openBoardStoryId = uuid(openBoardStory, "/id");

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
        ObjectNode openColumnBody = objectMapper.createObjectNode()
                .put("name", "Open")
                .put("position", 0)
                .put("doneColumn", false);
        openColumnBody.set("statusIds", objectMapper.createArrayNode().add(statusId(setup, "open").toString()));
        postJson("/api/v1/boards/" + boardId + "/columns", openColumnBody);
        ObjectNode readyColumnBody = objectMapper.createObjectNode()
                .put("name", "Ready")
                .put("position", 1)
                .put("doneColumn", false);
        readyColumnBody.set("statusIds", objectMapper.createArrayNode().add(statusId(setup, "ready").toString()));
        JsonNode readyColumn = postJson("/api/v1/boards/" + boardId + "/columns", readyColumnBody);
        ObjectNode columnBody = objectMapper.createObjectNode()
                .put("name", "Done")
                .put("position", 2)
                .put("doneColumn", true);
        columnBody.set("statusIds", objectMapper.createArrayNode().add(statusId(setup, "done").toString()));
        JsonNode column = postJson("/api/v1/boards/" + boardId + "/columns", columnBody);
        assertThat(column.at("/doneColumn").asBoolean()).isTrue();
        ObjectNode savedFilterQuery = objectMapper.createObjectNode();
        savedFilterQuery.set("where", objectMapper.createObjectNode()
                .put("field", "reporterId")
                .put("operator", "eq")
                .put("value", actorId.toString()));
        JsonNode savedFilter = postJson("/api/v1/workspaces/" + workspaceId + "/saved-filters", objectMapper.createObjectNode()
                .put("name", "Reporter board lane")
                .put("visibility", "project")
                .put("projectId", projectId.toString())
                .set("query", savedFilterQuery));
        ObjectNode swimlaneBody = objectMapper.createObjectNode()
                .put("name", "Reporter query")
                .put("swimlaneType", "query")
                .put("savedFilterId", savedFilter.at("/id").asText())
                .put("position", 0)
                .put("enabled", true);
        JsonNode swimlane = postJson("/api/v1/boards/" + boardId + "/swimlanes", swimlaneBody);
        assertThat(swimlane.at("/swimlaneType").asText()).isEqualTo("query");
        assertThat(uuid(swimlane, "/savedFilterId")).isEqualTo(uuid(savedFilter, "/id"));
        assertThat(getJson("/api/v1/projects/" + projectId + "/boards")).hasSizeGreaterThanOrEqualTo(2);
        JsonNode boardWork = getJson("/api/v1/boards/" + boardId + "/work-items");
        assertThat(boardWork.at("/columns")).hasSize(3);
        assertThat(boardWork.at("/swimlanes/0/columns/0/workItems")).hasSize(3);
        JsonNode readyPeerStory = postJson("/api/v1/boards/" + boardId + "/work-items/" + boardPeerStoryId + "/move", objectMapper.createObjectNode()
                .put("targetColumnId", readyColumn.at("/id").asText()));
        JsonNode boardRankedStory = postJson("/api/v1/boards/" + boardId + "/work-items/" + storyId + "/move", objectMapper.createObjectNode()
                .put("targetColumnId", readyColumn.at("/id").asText())
                .put("previousWorkItemId", boardPeerStoryId.toString()));
        assertThat(boardRankedStory.at("/rank").asText()).isGreaterThan(readyPeerStory.at("/rank").asText());
        assertThat(uuid(boardRankedStory, "/statusId")).isEqualTo(statusId(setup, "ready"));
        HttpResponse<String> invalidBoardMove = post("/api/v1/boards/" + boardId + "/work-items/" + storyId + "/move", objectMapper.createObjectNode()
                .put("targetColumnId", readyColumn.at("/id").asText())
                .put("previousWorkItemId", openBoardStoryId.toString()));
        assertThat(invalidBoardMove.statusCode()).isEqualTo(400);

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
        assertThat(webhook.at("/previousSecretOverlapSeconds").asLong()).isEqualTo(Duration.ofHours(2).toSeconds());
        String originalWebhookKeyId = webhook.at("/secretKeyId").asText();
        JsonNode rotatedWebhook = patch("/api/v1/webhooks/" + webhookId, objectMapper.createObjectNode()
                .put("secret", "rotated-development-secret")
                .put("previousSecretOverlapSeconds", Duration.ofHours(1).toSeconds()));
        assertThat(rotatedWebhook.at("/previousSecretKeyId").asText()).isEqualTo(originalWebhookKeyId);
        assertThat(rotatedWebhook.at("/previousSecretOverlapSeconds").asLong()).isEqualTo(Duration.ofHours(1).toSeconds());
        assertThat(Duration.between(
                OffsetDateTime.parse(rotatedWebhook.at("/secretRotatedAt").asText()),
                OffsetDateTime.parse(rotatedWebhook.at("/previousSecretExpiresAt").asText())
        )).isEqualTo(Duration.ofHours(1));

        JsonNode emailProviderSettings = putJson("/api/v1/workspaces/" + workspaceId + "/email-provider-settings", objectMapper.createObjectNode()
                .put("provider", "smtp")
                .put("fromEmail", "automation@example.com")
                .put("smtpHost", "smtp.example.com")
                .put("smtpPort", 587)
                .put("smtpUsername", "automation-user")
                .put("smtpPassword", "automation-password")
                .put("smtpStartTlsEnabled", true)
                .put("smtpAuthEnabled", true)
                .put("active", true));
        assertThat(emailProviderSettings.at("/provider").asText()).isEqualTo("smtp");
        assertThat(emailProviderSettings.at("/smtpPasswordConfigured").asBoolean()).isTrue();
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/email-provider-settings").at("/smtpPassword").isMissingNode()).isTrue();

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
        assertThat(emails.at("/0/provider").asText()).isEqualTo("smtp");
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
                .put("importReviewExportsEnabled", true)
                .put("automationLimit", 5)
                .put("webhookLimit", 5)
                .put("emailLimit", 5)
                .put("importReviewExportLimit", 5)
                .put("webhookDryRun", true)
                .put("emailDryRun", true)
                .put("workerRunRetentionEnabled", true)
                .put("workerRunRetentionDays", 1)
                .put("workerRunExportBeforePrune", true)
                .put("workerRunPruningAutomaticEnabled", true)
                .put("workerRunPruningIntervalMinutes", 720)
                .put("workerRunPruningWindowStart", "01:00:00")
                .put("workerRunPruningWindowEnd", "04:00:00")
                .put("agentDispatchAttemptRetentionEnabled", true)
                .put("agentDispatchAttemptRetentionDays", 30)
                .put("agentDispatchAttemptExportBeforePrune", true)
                .put("agentDispatchAttemptPruningAutomaticEnabled", true)
                .put("agentDispatchAttemptPruningIntervalMinutes", 720)
                .put("agentDispatchAttemptPruningWindowStart", "01:00:00")
                .put("agentDispatchAttemptPruningWindowEnd", "04:00:00"));
        assertThat(workerSettings.at("/automationJobsEnabled").asBoolean()).isTrue();
        assertThat(workerSettings.at("/importReviewExportsEnabled").asBoolean()).isTrue();
        assertThat(workerSettings.at("/importReviewExportLimit").asInt()).isEqualTo(5);
        assertThat(workerSettings.at("/workerRunRetentionEnabled").asBoolean()).isTrue();
        assertThat(workerSettings.at("/workerRunPruningAutomaticEnabled").asBoolean()).isTrue();
        assertThat(workerSettings.at("/workerRunPruningIntervalMinutes").asInt()).isEqualTo(720);
        assertThat(workerSettings.at("/agentDispatchAttemptRetentionEnabled").asBoolean()).isTrue();
        assertThat(workerSettings.at("/agentDispatchAttemptPruningAutomaticEnabled").asBoolean()).isTrue();
        assertThat(workerSettings.at("/agentDispatchAttemptPruningIntervalMinutes").asInt()).isEqualTo(720);
        JsonNode workerRunExport = postJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs/export?limit=5", objectMapper.createObjectNode());
        assertThat(workerRunExport.at("/retentionEnabled").asBoolean()).isTrue();
        assertThat(workerRunExport.at("/exportJobId").isMissingNode() || workerRunExport.at("/exportJobId").isNull()).isFalse();
        JsonNode workerRunPrune = postJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs/prune", objectMapper.createObjectNode());
        assertThat(workerRunPrune.at("/runsPruned").asInt()).isEqualTo(0);
        jdbcTemplate.update("update automation_worker_runs set started_at = now() - interval '2 days' where workspace_id = ?", workspaceId);
        JsonNode emailWorkerRunExport = postJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs/export?limit=5&workerType=email&triggerType=manual&status=succeeded&startedFrom=2000-01-01T00:00:00Z&startedTo=2100-01-01T00:00:00Z", objectMapper.createObjectNode());
        assertThat(emailWorkerRunExport.at("/workerType").asText()).isEqualTo("email");
        assertThat(emailWorkerRunExport.at("/triggerType").asText()).isEqualTo("manual");
        assertThat(emailWorkerRunExport.at("/status").asText()).isEqualTo("succeeded");
        assertThat(emailWorkerRunExport.at("/runsIncluded").asInt()).isEqualTo(1);
        assertThat(emailWorkerRunExport.at("/runs")).hasSize(1);
        assertThat(emailWorkerRunExport.at("/runs/0/workerType").asText()).isEqualTo("email");
        JsonNode emailWorkerRunPrune = postJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs/prune?workerType=email&triggerType=manual&status=succeeded&startedFrom=2000-01-01T00:00:00Z&startedTo=2100-01-01T00:00:00Z", objectMapper.createObjectNode());
        assertThat(emailWorkerRunPrune.at("/workerType").asText()).isEqualTo("email");
        assertThat(emailWorkerRunPrune.at("/triggerType").asText()).isEqualTo("manual");
        assertThat(emailWorkerRunPrune.at("/status").asText()).isEqualTo("succeeded");
        assertThat(emailWorkerRunPrune.at("/runsPruned").asInt()).isEqualTo(1);
        JsonNode remainingWebhookRuns = getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs?workerType=webhook");
        assertThat(remainingWebhookRuns).isNotEmpty();

        JsonNode importJob = postJson("/api/v1/workspaces/" + workspaceId + "/import-jobs", objectMapper.createObjectNode()
                .put("provider", "jira"));
        UUID importJobId = uuid(importJob, "/id");
        assertThat(getJson("/api/v1/import-jobs/" + importJobId + "/records?status=pending&sourceType=issue")).isEmpty();
        JsonNode importRecord = postJson("/api/v1/import-jobs/" + importJobId + "/records", objectMapper.createObjectNode()
                .put("sourceType", "issue")
                .put("sourceId", "JIRA-1")
                .put("targetType", "work_item")
                .put("targetId", storyId.toString())
                .put("status", "imported"));
        assertThat(importRecord.at("/sourceId").asText()).isEqualTo("JIRA-1");
        ObjectNode manualImportPayload = objectMapper.createObjectNode();
        manualImportPayload.set("fields", objectMapper.createObjectNode()
                .put("summary", "  Imported: Manual    story  ")
                .set("issuetype", objectMapper.createObjectNode().put("name", "Story")));
        ((ObjectNode) manualImportPayload.get("fields"))
                .set("status", objectMapper.createObjectNode().put("name", "To Do"));
        JsonNode updatedImportRecord = patch("/api/v1/import-job-records/" + importRecord.at("/id").asText(), objectMapper.createObjectNode()
                .put("sourceType", "issue")
                .put("sourceId", "JIRA-1")
                .put("targetType", "work_item")
                .put("targetId", storyId.toString())
                .put("status", "imported")
                .set("rawPayload", manualImportPayload));
        assertThat(updatedImportRecord.at("/rawPayload/fields/summary").asText()).contains("Manual");
        JsonNode importRecordVersions = getJson("/api/v1/import-job-records/" + importRecord.at("/id").asText() + "/versions");
        assertThat(importRecordVersions).hasSize(2);
        assertThat(importRecordVersions.at("/0/changeType").asText()).isEqualTo("updated");
        JsonNode importRecordDiffs = getJson("/api/v1/import-job-records/" + importRecord.at("/id").asText() + "/version-diffs");
        assertThat(importRecordDiffs).hasSize(2);
        assertThat(importRecordDiffs.at("/0/version").asInt()).isEqualTo(2);
        assertThat(importRecordDiffs.at("/0/comparedToVersion").asInt()).isEqualTo(1);
        assertThat(hasDiffField(importRecordDiffs.at("/0/fields"), "rawPayload.fields.summary", "added")).isTrue();
        ObjectNode parseBody = objectMapper.createObjectNode()
                .put("content", """
                        {"issues":[{"key":"JIRA-2","fields":{"summary":"  Imported: Parsed    story  ","issuetype":{"name":"Story"},"status":{"name":"To Do"},"security":"Public"}}]}
                        """);
        JsonNode parsedImport = postJson("/api/v1/import-jobs/" + importJobId + "/parse", parseBody);
        assertThat(parsedImport.at("/recordsParsed").asInt()).isEqualTo(1);
        assertThat(parsedImport.at("/records/0/sourceId").asText()).isEqualTo("JIRA-2");
        JsonNode pendingIssueRecords = getJson("/api/v1/import-jobs/" + importJobId + "/records?status=pending&sourceType=issue");
        assertThat(pendingIssueRecords).hasSize(1);
        assertThat(pendingIssueRecords.at("/0/sourceId").asText()).isEqualTo("JIRA-2");
        ObjectNode presetConfig = objectMapper.createObjectNode();
        presetConfig.set("title", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("function", "trim"))
                .add(objectMapper.createObjectNode()
                        .put("function", "replace")
                        .put("target", "Imported: ")
                        .put("replacement", ""))
                .add(objectMapper.createObjectNode().put("function", "collapse_whitespace")));
        JsonNode transformPreset = postJson("/api/v1/workspaces/" + workspaceId + "/import-transform-presets", objectMapper.createObjectNode()
                .put("name", "Jira title cleanup")
                .put("description", "Reusable Jira title cleanup")
                .put("enabled", true)
                .set("transformationConfig", presetConfig));
        assertThat(getJson("/api/v1/workspaces/" + workspaceId + "/import-transform-presets")).hasSize(1);
        assertThat(getJson("/api/v1/import-transform-presets/" + transformPreset.at("/id").asText() + "/versions")).hasSize(1);

        ObjectNode mappingBody = objectMapper.createObjectNode()
                .put("name", "Jira story mapping")
                .put("provider", "jira")
                .put("sourceType", "issue")
                .put("targetType", "work_item")
                .put("projectId", projectId.toString())
                .put("transformPresetId", transformPreset.at("/id").asText())
                .put("enabled", true);
        mappingBody.set("fieldMapping", objectMapper.createObjectNode()
                .put("title", "fields.summary")
                .put("typeKey", "fields.issuetype.name")
                .put("statusKey", "fields.status.name")
                .put("descriptionMarkdown", "fields.description"));
        mappingBody.set("defaults", objectMapper.createObjectNode()
                .put("descriptionMarkdown", "Imported Jira issue"));
        JsonNode mapping = postJson("/api/v1/workspaces/" + workspaceId + "/import-mapping-templates", mappingBody);
        assertThat(uuid(mapping, "/transformPresetId")).isEqualTo(uuid(transformPreset, "/id"));
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
        assertThat(materialized.at("/materializationRunId").isMissingNode()).isFalse();
        JsonNode materializationRuns = getJson("/api/v1/import-jobs/" + importJobId + "/materialization-runs");
        assertThat(materializationRuns).hasSize(1);
        assertThat(materializationRuns.at("/0/transformPresetVersion").asInt()).isEqualTo(1);
        UUID importedWorkItemId = uuid(materialized, "/records/1/targetId");
        JsonNode importedWorkItem = getJson("/api/v1/work-items/" + importedWorkItemId);
        assertThat(importedWorkItem.at("/title").asText()).isEqualTo("Parsed story");
        assertThat(importedWorkItem.at("/visibility").asText()).isEqualTo("public");
        JsonNode parsedRecordVersions = getJson("/api/v1/import-job-records/" + materialized.at("/records/1/id").asText() + "/versions");
        assertThat(parsedRecordVersions.at("/0/changeType").asText()).isEqualTo("materialized_created");
        ObjectNode updatedPresetConfig = presetConfig.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) updatedPresetConfig.get("title"))
                .add(objectMapper.createObjectNode()
                        .put("function", "suffix")
                        .put("value", " v2"));
        JsonNode updatedTransformPreset = patch("/api/v1/import-transform-presets/" + transformPreset.at("/id").asText(), objectMapper.createObjectNode()
                .put("name", "Jira title cleanup")
                .put("description", "Reusable Jira title cleanup v2")
                .put("enabled", true)
                .set("transformationConfig", updatedPresetConfig));
        assertThat(updatedTransformPreset.at("/version").asInt()).isEqualTo(2);
        JsonNode presetVersions = getJson("/api/v1/import-transform-presets/" + transformPreset.at("/id").asText() + "/versions");
        assertThat(presetVersions).hasSize(2);
        assertThat(presetVersions.at("/0/version").asInt()).isEqualTo(2);
        JsonNode clonedTransformPreset = postJson(
                "/api/v1/import-transform-presets/" + transformPreset.at("/id").asText() + "/versions/" + presetVersions.at("/1/id").asText() + "/clone",
                objectMapper.createObjectNode()
                        .put("name", "Jira title cleanup v1 clone")
                        .put("enabled", true));
        assertThat(clonedTransformPreset.at("/version").asInt()).isEqualTo(1);
        assertThat(clonedTransformPreset.at("/transformationConfig/title")).hasSize(3);
        assertThat(getJson("/api/v1/import-transform-presets/" + clonedTransformPreset.at("/id").asText() + "/versions")).hasSize(1);
        JsonNode updatedMaterialized = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", true));
        assertThat(updatedMaterialized.at("/updated").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(updatedMaterialized.at("/skipped").asInt()).isZero();
        assertThat(getJson("/api/v1/work-items/" + importedWorkItemId).at("/title").asText()).isEqualTo("Parsed story v2");
        JsonNode skippedMaterialized = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        UUID skippedRunId = uuid(skippedMaterialized, "/materializationRunId");
        assertThat(skippedMaterialized.at("/created").asInt()).isZero();
        assertThat(skippedMaterialized.at("/skipped").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(skippedMaterialized.at("/conflicts").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode conflicts = getJson("/api/v1/import-jobs/" + importJobId + "/conflicts");
        assertThat(conflicts).hasSizeGreaterThanOrEqualTo(1);
        JsonNode parsedConflict = findRecordBySourceId(conflicts, "JIRA-2");
        assertThat(parsedConflict.at("/status").asText()).isEqualTo("conflict");
        assertThat(parsedConflict.at("/conflictStatus").asText()).isEqualTo("open");
        assertThat(parsedConflict.at("/conflictReason").asText()).contains("updateExisting is false");
        JsonNode parsedConflictVersions = getJson("/api/v1/import-job-records/" + parsedConflict.at("/id").asText() + "/versions");
        assertThat(parsedConflictVersions.at("/0/changeType").asText()).isEqualTo("conflict_opened");
        ObjectNode bulkResolveBody = objectMapper.createObjectNode().put("resolution", "create_new");
        bulkResolveBody.set("recordIds", objectMapper.createArrayNode().add(parsedConflict.at("/id").asText()));
        JsonNode bulkResolvedCreateNewConflict = postJson(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve",
                bulkResolveBody);
        assertThat(bulkResolvedCreateNewConflict.at("/resolved").asInt()).isEqualTo(1);
        JsonNode resolvedCreateNewConflict = bulkResolvedCreateNewConflict.at("/records/0");
        assertThat(resolvedCreateNewConflict.at("/status").asText()).isEqualTo("pending");
        assertThat(resolvedCreateNewConflict.at("/targetId").isMissingNode() || resolvedCreateNewConflict.at("/targetId").isNull()).isTrue();
        assertThat(resolvedCreateNewConflict.at("/conflictStatus").asText()).isEqualTo("resolved");
        JsonNode resolvedConflictVersions = getJson("/api/v1/import-job-records/" + parsedConflict.at("/id").asText() + "/versions");
        assertThat(resolvedConflictVersions.at("/0/changeType").asText()).isEqualTo("conflict_resolved");
        JsonNode rerunMaterialized = postJson("/api/v1/import-materialization-runs/" + skippedRunId + "/rerun", objectMapper.createObjectNode()
                .put("limit", 10));
        assertThat(rerunMaterialized.at("/materializationRunId").asText()).isNotEqualTo(skippedRunId.toString());
        assertThat(rerunMaterialized.at("/created").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode rerunParsedRecord = findRecordBySourceId(rerunMaterialized.at("/records"), "JIRA-2");
        assertThat(uuid(rerunParsedRecord, "/targetId")).isNotEqualTo(importedWorkItemId);
        JsonNode remainingConflicts = getJson("/api/v1/import-jobs/" + importJobId + "/conflicts");
        JsonNode manualConflict = findRecordBySourceId(remainingConflicts, "JIRA-1");
        JsonNode resolvedUpdateConflict = postJson(
                "/api/v1/import-job-records/" + manualConflict.at("/id").asText() + "/resolve-conflict",
                objectMapper.createObjectNode().put("resolution", "update_existing"));
        assertThat(resolvedUpdateConflict.at("/status").asText()).isEqualTo("pending");
        assertThat(uuid(resolvedUpdateConflict, "/targetId")).isEqualTo(storyId);
        JsonNode overrideRerunMaterialized = postJson("/api/v1/import-materialization-runs/" + skippedRunId + "/rerun", objectMapper.createObjectNode()
                .put("limit", 10)
                .put("updateExisting", true));
        assertThat(overrideRerunMaterialized.at("/updated").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(overrideRerunMaterialized.at("/skipped").asInt()).isZero();
        assertThat(getJson("/api/v1/work-items/" + storyId).at("/title").asText()).isEqualTo("Manual story v2");
        JsonNode finalSkippedMaterialized = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        assertThat(finalSkippedMaterialized.at("/conflicts").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode finalConflicts = getJson("/api/v1/import-jobs/" + importJobId + "/conflicts");
        manualConflict = findRecordBySourceId(finalConflicts, "JIRA-1");
        JsonNode resolvedSkipConflict = postJson(
                "/api/v1/import-job-records/" + manualConflict.at("/id").asText() + "/resolve-conflict",
                objectMapper.createObjectNode().put("resolution", "skip"));
        assertThat(resolvedSkipConflict.at("/status").asText()).isEqualTo("skipped");
        assertThat(resolvedSkipConflict.at("/conflictResolution").asText()).isEqualTo("skip");
        JsonNode openConflictRecords = getJson("/api/v1/import-jobs/" + importJobId + "/records?status=conflict&conflictStatus=open&sourceType=issue");
        assertThat(openConflictRecords).hasSizeGreaterThanOrEqualTo(1);
        ObjectNode filteredResolveBody = objectMapper.createObjectNode()
                .put("scope", "filtered")
                .put("status", "conflict")
                .put("conflictStatus", "open")
                .put("sourceType", "issue")
                .put("resolution", "update_existing");
        JsonNode filteredResolvePreview = postJson(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve-preview",
                ((ObjectNode) filteredResolveBody.deepCopy()).put("pageSize", 1));
        assertThat(filteredResolvePreview.at("/scope").asText()).isEqualTo("filtered");
        assertThat(filteredResolvePreview.at("/matched").asInt()).isEqualTo(openConflictRecords.size());
        assertThat(filteredResolvePreview.at("/returned").asInt()).isEqualTo(1);
        assertThat(filteredResolvePreview.at("/page").asInt()).isZero();
        assertThat(filteredResolvePreview.at("/pageSize").asInt()).isEqualTo(1);
        assertThat(filteredResolvePreview.at("/maxResolutionBatchSize").asInt()).isEqualTo(500);
        HttpResponse<String> blockedFilteredPreview = post(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve-preview",
                ((ObjectNode) filteredResolveBody.deepCopy()).put("pageSize", 201));
        assertThat(blockedFilteredPreview.statusCode()).isEqualTo(400);
        HttpResponse<String> blockedFilteredResolve = post(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve",
                ((ObjectNode) filteredResolveBody.deepCopy())
                        .put("confirmation", "RESOLVE FILTERED CONFLICTS")
                        .put("expectedCount", openConflictRecords.size() + 1));
        assertThat(blockedFilteredResolve.statusCode()).isEqualTo(400);
        JsonNode filteredResolvedConflicts = postJson(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve",
                filteredResolveBody
                        .put("confirmation", "RESOLVE FILTERED CONFLICTS")
                        .put("expectedCount", openConflictRecords.size()));
        assertThat(filteredResolvedConflicts.at("/scope").asText()).isEqualTo("filtered");
        assertThat(filteredResolvedConflicts.at("/resolved").asInt()).isEqualTo(openConflictRecords.size());
        JsonNode reopenedFilteredConflicts = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        assertThat(reopenedFilteredConflicts.at("/conflicts").asInt()).isGreaterThanOrEqualTo(1);
        ObjectNode retargetBody = objectMapper.createObjectNode()
                .put("name", "Jira title cleanup v1 retarget clone")
                .put("enabled", true);
        retargetBody.set("mappingTemplateIds", objectMapper.createArrayNode().add(mapping.at("/id").asText()));
        JsonNode retargetPreview = postJson(
                "/api/v1/import-transform-presets/" + transformPreset.at("/id").asText() + "/versions/" + presetVersions.at("/1/id").asText() + "/retarget-preview",
                retargetBody);
        assertThat(retargetPreview.at("/clonedPreset").isMissingNode() || retargetPreview.at("/clonedPreset").isNull()).isTrue();
        assertThat(retargetPreview.at("/templates/0/id").asText()).isEqualTo(mapping.at("/id").asText());
        JsonNode retargetApplied = postJson(
                "/api/v1/import-transform-presets/" + transformPreset.at("/id").asText() + "/versions/" + presetVersions.at("/1/id").asText() + "/retarget",
                retargetBody);
        UUID retargetedPresetId = uuid(retargetApplied, "/clonedPreset/id");
        assertThat(uuid(retargetApplied, "/templates/0/newTransformPresetId")).isEqualTo(retargetedPresetId);
        JsonNode retargetedMapping = findRecordById(getJson("/api/v1/workspaces/" + workspaceId + "/import-mapping-templates"), mapping.at("/id").asText());
        assertThat(uuid(retargetedMapping, "/transformPresetId")).isEqualTo(retargetedPresetId);
        JsonNode updatedMaterializationRuns = getJson("/api/v1/import-jobs/" + importJobId + "/materialization-runs");
        assertThat(updatedMaterializationRuns).hasSize(7);
        assertThat(updatedMaterializationRuns.at("/0/recordsSkipped").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(updatedMaterializationRuns.at("/0/mappingRulesSnapshot/typeTranslations")).hasSize(1);
        HttpResponse<String> blockedComplete = post("/api/v1/import-jobs/" + importJobId + "/complete", objectMapper.createObjectNode());
        assertThat(blockedComplete.statusCode()).isEqualTo(409);
        HttpResponse<String> blockedBooleanComplete = post("/api/v1/import-jobs/" + importJobId + "/complete", objectMapper.createObjectNode()
                .put("acceptOpenConflicts", true));
        assertThat(blockedBooleanComplete.statusCode()).isEqualTo(409);
        JsonNode completedImportJob = postJson("/api/v1/import-jobs/" + importJobId + "/complete", objectMapper.createObjectNode()
                .put("acceptOpenConflicts", true)
                .put("openConflictConfirmation", "COMPLETE WITH OPEN CONFLICTS")
                .put("openConflictReason", "Manual import exercise accepts these staged open conflicts."));
        assertThat(completedImportJob.at("/status").asText()).isEqualTo("completed");
        assertThat(completedImportJob.at("/openConflictCompletionAccepted").asBoolean()).isTrue();
        assertThat(completedImportJob.at("/openConflictCompletionCount").asInt()).isGreaterThan(0);
        assertThat(uuid(completedImportJob, "/openConflictCompletedById")).isEqualTo(actorId);
        assertThat(completedImportJob.at("/openConflictCompletedAt").isMissingNode() || completedImportJob.at("/openConflictCompletedAt").isNull()).isFalse();
        assertThat(completedImportJob.at("/openConflictCompletionReason").asText()).contains("Manual import exercise");
        JsonNode replayedEvents = postJson("/api/v1/workspaces/" + workspaceId + "/domain-events/replay", objectMapper.createObjectNode());
        assertThat(replayedEvents.at("/eventsMatched").asInt()).isGreaterThan(0);
        JsonNode activity = getJson("/api/v1/workspaces/" + workspaceId + "/activity?limit=100");
        assertThat(eventTypes(activity.at("/items"), "eventType"))
                .contains("import_transform_preset.clone_retargeted", "import_job.completed", "import_job_record.conflict_resolved");
        JsonNode audit = getJson("/api/v1/workspaces/" + workspaceId + "/audit-log?limit=100");
        assertThat(eventTypes(audit.at("/items"), "action"))
                .contains("import_transform_preset.clone_retargeted", "import_job.completed", "import_job_record.conflict_resolved");
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

    private JsonNode putJson(String path, JsonNode body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .method("PUT", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
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

    private JsonNode findRecordBySourceId(JsonNode records, String sourceId) {
        for (JsonNode record : records) {
            if (sourceId.equals(record.at("/sourceId").asText())) {
                return record;
            }
        }
        throw new IllegalStateException("Record not found: " + sourceId);
    }

    private JsonNode findRecordById(JsonNode records, String id) {
        for (JsonNode record : records) {
            if (id.equals(record.at("/id").asText())) {
                return record;
            }
        }
        throw new IllegalStateException("Record not found: " + id);
    }

    private boolean hasDiffField(JsonNode fields, String path, String changeType) {
        for (JsonNode field : fields) {
            if (path.equals(field.at("/path").asText()) && changeType.equals(field.at("/changeType").asText())) {
                return true;
            }
        }
        return false;
    }

    private List<String> eventTypes(JsonNode records, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode record : records) {
            values.add(record.at("/" + field).asText());
        }
        return values;
    }
}
