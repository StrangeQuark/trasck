package com.strangequark.trasck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
class ImportSampleFixtureIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13.1-alpine")
            .withDatabaseName("trasck_import_samples_test")
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
    }

    @Test
    void importSamplesExerciseFullParseMaterializeConflictRerunAndCompletionFlow() throws Exception {
        JsonNode setup = postSetup();
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        accessToken = login(setup);

        JsonNode initialImportSettings = getJson("/api/v1/workspaces/" + workspaceId + "/import-settings");
        assertThat(initialImportSettings.at("/sampleJobsEnabled").asBoolean()).isTrue();
        assertThat(initialImportSettings.at("/sampleJobsAvailable").asBoolean()).isTrue();
        JsonNode disabledImportSettings = patch("/api/v1/workspaces/" + workspaceId + "/import-settings", objectMapper.createObjectNode()
                .put("sampleJobsEnabled", false));
        assertThat(disabledImportSettings.at("/sampleJobsAvailable").asBoolean()).isFalse();
        assertThat(get("/api/v1/workspaces/" + workspaceId + "/import-samples").statusCode()).isEqualTo(403);
        JsonNode enabledImportSettings = patch("/api/v1/workspaces/" + workspaceId + "/import-settings", objectMapper.createObjectNode()
                .put("sampleJobsEnabled", true));
        assertThat(enabledImportSettings.at("/sampleJobsAvailable").asBoolean()).isTrue();

        JsonNode sampleCatalog = getJson("/api/v1/workspaces/" + workspaceId + "/import-samples");
        assertThat(sampleKeys(sampleCatalog)).contains("csv", "jira", "rally");
        JsonNode sampleJob = postJson("/api/v1/workspaces/" + workspaceId + "/import-samples/csv/jobs", objectMapper.createObjectNode()
                .put("projectId", projectId.toString())
                .put("createMappingTemplate", true));
        assertThat(sampleJob.at("/sample/key").asText()).isEqualTo("csv");
        assertThat(sampleJob.at("/parse/recordsParsed").asInt()).isEqualTo(3);
        assertThat(sampleJob.at("/mappingTemplate/provider").asText()).isEqualTo("csv");

        JsonNode workerSettings = getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-settings");
        assertThat(workerSettings.at("/importConflictResolutionEnabled").asBoolean()).isFalse();
        assertThat(workerSettings.at("/importConflictResolutionLimit").asInt()).isEqualTo(10);
        JsonNode updatedWorkerSettings = patch("/api/v1/workspaces/" + workspaceId + "/automation-worker-settings", objectMapper.createObjectNode()
                .put("importConflictResolutionEnabled", true)
                .put("importConflictResolutionLimit", 5));
        assertThat(updatedWorkerSettings.at("/importConflictResolutionEnabled").asBoolean()).isTrue();
        assertThat(updatedWorkerSettings.at("/importConflictResolutionLimit").asInt()).isEqualTo(5);

        List<SampleSpec> samples = List.of(
                new SampleSpec(
                        "CSV",
                        "csv",
                        "row",
                        "docs/import-samples/sample.csv",
                        "CSV-1",
                        "CSV-2",
                        "CSV-3",
                        "title",
                        "visibility",
                        "CSV story",
                        "CSV story updated"
                ),
                new SampleSpec(
                        "Jira",
                        "jira",
                        "issue",
                        "docs/import-samples/jira-issues.json",
                        "JIRA-1",
                        "JIRA-2",
                        "JIRA-3",
                        "fields.summary",
                        "fields.security",
                        "Jira story",
                        "Jira story updated"
                ),
                new SampleSpec(
                        "Rally",
                        "rally",
                        "artifact",
                        "docs/import-samples/rally-artifacts.json",
                        "US101",
                        "DE102",
                        "US103",
                        "Name",
                        "Visibility",
                        "Rally story",
                        "Rally story updated"
                )
        );

        for (SampleSpec sample : samples) {
            exerciseSample(setup, workspaceId, projectId, sample);
        }

        exerciseLargeConflictResolutionJobOperations(workspaceId);

        JsonNode projectSummary = getJson("/api/v1/reports/projects/" + projectId
                + "/dashboard-summary?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z");
        assertThat(projectSummary.at("/importCompletions/completedJobs").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(projectSummary.at("/importCompletions/completedWithOpenConflicts").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(projectSummary.at("/importCompletions/acceptedOpenConflictCount").asLong()).isGreaterThanOrEqualTo(9);
        assertThat(projectSummary.at("/importCompletions/lastOpenConflictCompletedAt").isMissingNode()
                || projectSummary.at("/importCompletions/lastOpenConflictCompletedAt").isNull()).isFalse();
        assertThat(widgetTypes(projectSummary.at("/widgets"))).contains("import_completion_summary");

        JsonNode workspaceSummary = getJson("/api/v1/reports/workspaces/" + workspaceId
                + "/dashboard-summary?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z");
        assertThat(workspaceSummary.at("/importCompletions/completedWithOpenConflicts").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(widgetTypes(workspaceSummary.at("/widgets"))).contains("portfolio_import_completion_summary");
    }

    private void exerciseLargeConflictResolutionJobOperations(UUID workspaceId) throws Exception {
        JsonNode importJob = postJson("/api/v1/workspaces/" + workspaceId + "/import-jobs", objectMapper.createObjectNode()
                .put("provider", "csv"));
        UUID importJobId = uuid(importJob, "/id");
        insertSyntheticOpenConflicts(importJobId, "large", 501);

        JsonNode preview = postJson("/api/v1/import-jobs/" + importJobId + "/conflicts/resolve-preview", conflictResolutionBody("large", 501)
                .put("pageSize", 25));
        assertThat(preview.at("/matched").asInt()).isEqualTo(501);
        assertThat(preview.at("/hasMore").asBoolean()).isTrue();
        assertThat(post("/api/v1/import-jobs/" + importJobId + "/conflicts/resolve", conflictResolutionBody("large", 501)).statusCode()).isEqualTo(400);

        JsonNode queued = postJson("/api/v1/import-jobs/" + importJobId + "/conflicts/resolve-async", conflictResolutionBody("large", 501));
        UUID resolutionJobId = uuid(queued, "/id");
        assertThat(queued.at("/status").asText()).isEqualTo("queued");

        JsonNode cancelled = postJson("/api/v1/import-conflict-resolution-jobs/" + resolutionJobId + "/cancel", objectMapper.createObjectNode());
        assertThat(cancelled.at("/status").asText()).isEqualTo("cancelled");
        JsonNode skippedWorkerRun = postJson("/api/v1/workspaces/" + workspaceId + "/import-conflict-resolution-jobs/process?limit=5", objectMapper.createObjectNode());
        assertThat(skippedWorkerRun.at("/processed").asInt()).isZero();

        JsonNode retried = postJson("/api/v1/import-conflict-resolution-jobs/" + resolutionJobId + "/retry", objectMapper.createObjectNode());
        assertThat(retried.at("/status").asText()).isEqualTo("queued");
        insertSyntheticOpenConflict(importJobId, "large", "large-extra");
        JsonNode failedWorkerRun = postJson("/api/v1/workspaces/" + workspaceId + "/import-conflict-resolution-jobs/process?limit=5", objectMapper.createObjectNode());
        assertThat(failedWorkerRun.at("/processed").asInt()).isEqualTo(1);
        assertThat(failedWorkerRun.at("/failed").asInt()).isEqualTo(1);
        assertThat(getJson("/api/v1/import-conflict-resolution-jobs/" + resolutionJobId).at("/status").asText()).isEqualTo("failed");

        jdbcTemplate.update("delete from import_job_records where import_job_id = ? and source_id = ?", importJobId, "large-extra");
        JsonNode secondRetry = postJson("/api/v1/import-conflict-resolution-jobs/" + resolutionJobId + "/retry", objectMapper.createObjectNode());
        assertThat(secondRetry.at("/status").asText()).isEqualTo("queued");
        JsonNode completedWorkerRun = postJson("/api/v1/workspaces/" + workspaceId + "/import-conflict-resolution-jobs/process?limit=5", objectMapper.createObjectNode());
        assertThat(completedWorkerRun.at("/processed").asInt()).isEqualTo(1);
        assertThat(completedWorkerRun.at("/completed").asInt()).isEqualTo(1);
        assertThat(completedWorkerRun.at("/jobs/0/resolvedCount").asInt()).isEqualTo(501);
        assertThat(openConflictCount(importJobId)).isZero();
        JsonNode workerRuns = getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs");
        assertThat(hasWorkerRun(workerRuns, "import_conflict_resolution", "succeeded")).isTrue();
        assertThat(hasWorkerRun(workerRuns, "import_conflict_resolution", "failed")).isTrue();
        JsonNode filteredWorkerRuns = getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-runs?workerType=import_conflict_resolution");
        assertThat(filteredWorkerRuns).isNotEmpty();
        assertThat(allWorkerRunsHaveType(filteredWorkerRuns, "import_conflict_resolution")).isTrue();
        JsonNode workerHealth = getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-health");
        assertThat(hasWorkerHealth(workerHealth, "import_conflict_resolution", "succeeded")).isTrue();
        JsonNode filteredWorkerHealth = getJson("/api/v1/workspaces/" + workspaceId + "/automation-worker-health?workerType=import_conflict_resolution");
        assertThat(filteredWorkerHealth).isNotEmpty();
        assertThat(allWorkerHealthRowsHaveType(filteredWorkerHealth, "import_conflict_resolution")).isTrue();

        JsonNode workspaceJobs = getJson("/api/v1/workspaces/" + workspaceId + "/import-conflict-resolution-jobs?status=completed");
        assertThat(workspaceJobs).isNotEmpty();

        ObjectNode replayRequest = objectMapper.createObjectNode().put("includePublished", true);
        replayRequest.set("consumerKeys", objectMapper.createArrayNode().add("activity-projection").add("audit-projection"));
        JsonNode replay = postJson("/api/v1/workspaces/" + workspaceId + "/domain-events/replay", replayRequest);
        assertThat(replay.at("/deliveriesReset").asInt()).isGreaterThan(0);
        assertProjectedImportConflictResolutionJobEvent(workspaceId, "import_conflict_resolution_job.cancelled");
        assertProjectedImportConflictResolutionJobEvent(workspaceId, "import_conflict_resolution_job.failed");
        assertProjectedImportConflictResolutionJobEvent(workspaceId, "import_conflict_resolution_job.completed");
    }

    private void exerciseSample(JsonNode setup, UUID workspaceId, UUID projectId, SampleSpec sample) throws Exception {
        JsonNode importJob = postJson("/api/v1/workspaces/" + workspaceId + "/import-jobs", objectMapper.createObjectNode()
                .put("provider", sample.provider()));
        UUID importJobId = uuid(importJob, "/id");

        JsonNode parsed = postJson("/api/v1/import-jobs/" + importJobId + "/parse", objectMapper.createObjectNode()
                .put("sourceType", sample.sourceType())
                .put("content", Files.readString(Path.of(sample.path()))));
        assertThat(parsed.at("/recordsParsed").asInt()).isEqualTo(3);
        assertThat(findRecordBySourceId(parsed.at("/records"), sample.firstSourceId()).at("/status").asText()).isEqualTo("pending");

        JsonNode mapping = createMapping(workspaceId, projectId, sample);
        JsonNode materialized = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        assertThat(materialized.at("/created").asInt()).isEqualTo(3);

        JsonNode firstRecord = findRecordBySourceId(materialized.at("/records"), sample.firstSourceId());
        JsonNode bugRecord = findRecordBySourceId(materialized.at("/records"), sample.bugSourceId());
        JsonNode doneRecord = findRecordBySourceId(materialized.at("/records"), sample.doneSourceId());
        JsonNode firstWorkItem = getJson("/api/v1/work-items/" + firstRecord.at("/targetId").asText());
        JsonNode bugWorkItem = getJson("/api/v1/work-items/" + bugRecord.at("/targetId").asText());
        JsonNode doneWorkItem = getJson("/api/v1/work-items/" + doneRecord.at("/targetId").asText());
        assertThat(firstWorkItem.at("/title").asText()).isEqualTo(sample.expectedFirstTitle());
        assertThat(firstWorkItem.at("/visibility").asText()).isEqualTo("public");
        assertThat(uuid(firstWorkItem, "/statusId")).isEqualTo(statusId(setup, "open"));
        assertThat(uuid(bugWorkItem, "/typeId")).isEqualTo(workItemTypeId(setup, "bug"));
        assertThat(uuid(doneWorkItem, "/statusId")).isEqualTo(statusId(setup, "done"));

        ObjectNode updatedPayload = firstRecord.at("/rawPayload").deepCopy();
        setTextAtPath(updatedPayload, sample.titlePath(), "Imported: " + sample.expectedUpdatedTitle());
        JsonNode updatedRecord = patch("/api/v1/import-job-records/" + firstRecord.at("/id").asText(), objectMapper.createObjectNode()
                .put("sourceType", firstRecord.at("/sourceType").asText())
                .put("sourceId", firstRecord.at("/sourceId").asText())
                .put("targetType", "work_item")
                .put("targetId", firstRecord.at("/targetId").asText())
                .put("status", firstRecord.at("/status").asText())
                .set("rawPayload", updatedPayload));
        assertThat(updatedRecord.at("/rawPayload").toString()).contains(sample.expectedUpdatedTitle());

        JsonNode recordDiffs = getJson("/api/v1/import-job-records/" + firstRecord.at("/id").asText() + "/version-diffs");
        assertThat(recordDiffs).isNotEmpty();
        assertThat(hasDiffField(recordDiffs.at("/0/fields"), "rawPayload." + sample.titlePath(), "changed")).isTrue();
        JsonNode jobDiffs = getJson("/api/v1/import-jobs/" + importJobId + "/version-diffs");
        assertThat(jobDiffs.at("/recordCount").asInt()).isEqualTo(3);
        assertThat(jobDiffs.at("/diffCount").asInt()).isGreaterThan(0);
        JsonNode jobDiffExport = getJson("/api/v1/import-jobs/" + importJobId + "/version-diffs/export");
        assertThat(uuid(jobDiffExport, "/importJob/id")).isEqualTo(importJobId);
        assertThat(jobDiffExport.at("/generatedAt").isMissingNode() || jobDiffExport.at("/generatedAt").isNull()).isFalse();
        JsonNode exportJob = postJson("/api/v1/import-jobs/" + importJobId + "/version-diffs/export-jobs", objectMapper.createObjectNode());
        assertThat(exportJob.at("/exportType").asText()).isEqualTo("import_job_version_diffs");
        UUID exportJobId = uuid(exportJob, "/id");
        JsonNode csvExportJob = postJson("/api/v1/import-jobs/" + importJobId + "/version-diffs/export-jobs", objectMapper.createObjectNode()
                .put("format", "csv")
                .put("filterColumn", "path")
                .put("filter", sample.titlePath()));
        assertThat(csvExportJob.at("/filename").asText()).endsWith(".csv");
        UUID csvExportJobId = uuid(csvExportJob, "/id");
        JsonNode listedExportJobs = getJson("/api/v1/workspaces/" + workspaceId + "/export-jobs?exportType=import_job_version_diffs&limit=10");
        assertThat(listedExportJobs.at("/items")).isNotEmpty();
        HttpResponse<String> downloadedExport = get("/api/v1/workspaces/" + workspaceId + "/export-jobs/" + exportJobId + "/download");
        assertThat(downloadedExport.statusCode()).isEqualTo(200);
        assertThat(downloadedExport.headers().firstValue("Content-Disposition")).hasValueSatisfying(value -> assertThat(value).contains("import-job-version-diffs-"));
        assertThat(downloadedExport.body()).contains(importJobId.toString(), "diffs");
        HttpResponse<String> downloadedCsvExport = get("/api/v1/workspaces/" + workspaceId + "/export-jobs/" + csvExportJobId + "/download");
        assertThat(downloadedCsvExport.statusCode()).isEqualTo(200);
        assertThat(downloadedCsvExport.headers().firstValue("Content-Type")).hasValueSatisfying(value -> assertThat(value).contains("text/csv"));
        assertThat(downloadedCsvExport.body()).contains("Source,Status,Version,Change,Field,Previous,Current", sample.titlePath());

        JsonNode conflictRun = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        UUID conflictRunId = uuid(conflictRun, "/materializationRunId");
        assertThat(conflictRun.at("/conflicts").asInt()).isEqualTo(3);
        JsonNode preview = previewConflicts(importJobId, sample.sourceType(), 3);
        assertThat(preview.at("/returned").asInt()).isEqualTo(2);
        assertThat(preview.at("/hasMore").asBoolean()).isTrue();

        runFilteredConflictResolution(importJobId, sample.sourceType(), 3);
        JsonNode exactRerun = postJson("/api/v1/import-materialization-runs/" + conflictRunId + "/rerun", objectMapper.createObjectNode()
                .put("limit", 10));
        assertThat(exactRerun.at("/conflicts").asInt()).isEqualTo(3);

        runFilteredConflictResolution(importJobId, sample.sourceType(), 3);
        JsonNode modifiedRerun = postJson("/api/v1/import-materialization-runs/" + conflictRunId + "/rerun", objectMapper.createObjectNode()
                .put("limit", 10)
                .put("updateExisting", true));
        assertThat(modifiedRerun.at("/updated").asInt()).isEqualTo(3);
        assertThat(getJson("/api/v1/work-items/" + firstRecord.at("/targetId").asText()).at("/title").asText())
                .isEqualTo(sample.expectedUpdatedTitle());

        JsonNode finalConflictRun = postJson("/api/v1/import-jobs/" + importJobId + "/materialize", objectMapper.createObjectNode()
                .put("mappingTemplateId", mapping.at("/id").asText())
                .put("limit", 10)
                .put("updateExisting", false));
        assertThat(finalConflictRun.at("/conflicts").asInt()).isEqualTo(3);
        JsonNode completed = postJson("/api/v1/import-jobs/" + importJobId + "/complete", objectMapper.createObjectNode()
                .put("acceptOpenConflicts", true)
                .put("openConflictConfirmation", "COMPLETE WITH OPEN CONFLICTS")
                .put("openConflictReason", sample.label() + " sample fixture leaves final conflicts open for guarded completion coverage."));
        assertThat(completed.at("/status").asText()).isEqualTo("completed");
        assertThat(completed.at("/openConflictCompletionAccepted").asBoolean()).isTrue();
        assertThat(completed.at("/openConflictCompletionCount").asInt()).isEqualTo(3);
    }

    private JsonNode createMapping(UUID workspaceId, UUID projectId, SampleSpec sample) throws Exception {
        ObjectNode transformConfig = objectMapper.createObjectNode();
        transformConfig.set("title", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("function", "trim"))
                .add(objectMapper.createObjectNode()
                        .put("function", "replace")
                        .put("target", "Imported: ")
                        .put("replacement", ""))
                .add(objectMapper.createObjectNode().put("function", "collapse_whitespace")));
        transformConfig.set("descriptionMarkdown", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("function", "trim")));
        JsonNode transformPreset = postJson("/api/v1/workspaces/" + workspaceId + "/import-transform-presets", objectMapper.createObjectNode()
                .put("name", sample.label() + " fixture cleanup " + UUID.randomUUID())
                .put("description", "Fixture transform preset for " + sample.label())
                .put("enabled", true)
                .set("transformationConfig", transformConfig));

        ObjectNode mappingBody = objectMapper.createObjectNode()
                .put("name", sample.label() + " fixture mapping " + UUID.randomUUID())
                .put("provider", sample.provider())
                .put("sourceType", sample.sourceType())
                .put("targetType", "work_item")
                .put("projectId", projectId.toString())
                .put("transformPresetId", transformPreset.at("/id").asText())
                .put("enabled", true);
        ObjectNode fieldMapping = objectMapper.createObjectNode();
        fieldMapping.set("title", textArray("title", "fields.summary", "Name"));
        fieldMapping.set("typeKey", textArray("type", "fields.issuetype.name", "_type"));
        fieldMapping.set("statusKey", textArray("status", "fields.status.name", "ScheduleState"));
        fieldMapping.set("descriptionMarkdown", textArray("description", "fields.description", "Description"));
        mappingBody.set("fieldMapping", fieldMapping);
        mappingBody.set("defaults", objectMapper.createObjectNode()
                .put("descriptionMarkdown", "Imported through sample fixture"));
        JsonNode mapping = postJson("/api/v1/workspaces/" + workspaceId + "/import-mapping-templates", mappingBody);

        createTypeTranslation(mapping, "Story", "story");
        createTypeTranslation(mapping, "User Story", "story");
        createTypeTranslation(mapping, "HierarchicalRequirement", "story");
        createTypeTranslation(mapping, "Bug", "bug");
        createTypeTranslation(mapping, "Defect", "bug");
        createStatusTranslation(mapping, "To Do", "open");
        createStatusTranslation(mapping, "Defined", "open");
        createStatusTranslation(mapping, "Open", "open");
        createStatusTranslation(mapping, "In Progress", "in_progress");
        createStatusTranslation(mapping, "In-Progress", "in_progress");
        createStatusTranslation(mapping, "Done", "done");
        createStatusTranslation(mapping, "Accepted", "done");
        createStatusTranslation(mapping, "Closed", "done");
        postJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/value-lookups", objectMapper.createObjectNode()
                .put("sourceField", sample.visibilityPath())
                .put("sourceValue", "Public")
                .put("targetField", "visibility")
                .put("targetValue", "public")
                .put("enabled", true));
        return mapping;
    }

    private JsonNode previewConflicts(UUID importJobId, String sourceType, int expectedCount) throws Exception {
        return postJson(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve-preview",
                conflictResolutionBody(sourceType, expectedCount)
                        .put("pageSize", 2));
    }

    private JsonNode runFilteredConflictResolution(UUID importJobId, String sourceType, int expectedCount) throws Exception {
        JsonNode queued = postJson(
                "/api/v1/import-jobs/" + importJobId + "/conflicts/resolve-async",
                conflictResolutionBody(sourceType, expectedCount));
        UUID resolutionJobId = uuid(queued, "/id");
        assertThat(queued.at("/status").asText()).isEqualTo("queued");
        assertThat(queued.at("/matchedCount").asInt()).isEqualTo(expectedCount);
        assertThat(getJson("/api/v1/import-jobs/" + importJobId + "/conflict-resolution-jobs")).isNotEmpty();
        assertThat(getJson("/api/v1/import-conflict-resolution-jobs/" + resolutionJobId).at("/status").asText()).isEqualTo("queued");

        JsonNode completed = postJson("/api/v1/import-conflict-resolution-jobs/" + resolutionJobId + "/run", objectMapper.createObjectNode());
        assertThat(completed.at("/status").asText()).isEqualTo("completed");
        assertThat(completed.at("/resolvedCount").asInt()).isEqualTo(expectedCount);
        return completed;
    }

    private ObjectNode conflictResolutionBody(String sourceType, int expectedCount) {
        return objectMapper.createObjectNode()
                .put("scope", "filtered")
                .put("status", "conflict")
                .put("conflictStatus", "open")
                .put("sourceType", sourceType)
                .put("resolution", "update_existing")
                .put("confirmation", "RESOLVE FILTERED CONFLICTS")
                .put("expectedCount", expectedCount);
    }

    private void insertSyntheticOpenConflicts(UUID importJobId, String sourceType, int count) {
        for (int index = 1; index <= count; index++) {
            insertSyntheticOpenConflict(importJobId, sourceType, sourceType + "-" + index);
        }
    }

    private void insertSyntheticOpenConflict(UUID importJobId, String sourceType, String sourceId) {
        jdbcTemplate.update("""
                insert into import_job_records (
                    import_job_id,
                    source_type,
                    source_id,
                    status,
                    raw_payload,
                    conflict_status,
                    conflict_reason,
                    conflict_detected_at
                ) values (?, ?, ?, 'conflict', ?::jsonb, 'open', 'synthetic large conflict set', now())
                """,
                importJobId,
                sourceType,
                sourceId,
                "{\"title\":\"Synthetic conflict " + sourceId + "\"}"
        );
    }

    private int openConflictCount(UUID importJobId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from import_job_records where import_job_id = ? and conflict_status = 'open'",
                Integer.class,
                importJobId
        );
        return count == null ? 0 : count;
    }

    private void assertProjectedImportConflictResolutionJobEvent(UUID workspaceId, String eventType) {
        Integer activityCount = jdbcTemplate.queryForObject("""
                select count(*) from activity_events
                where workspace_id = ?
                  and entity_type = 'workspace'
                  and entity_id = ?
                  and event_type = ?
                """, Integer.class, workspaceId, workspaceId, eventType);
        Integer auditCount = jdbcTemplate.queryForObject("""
                select count(*) from audit_log_entries
                where workspace_id = ?
                  and target_type = 'import_conflict_resolution_job'
                  and action = ?
                """, Integer.class, workspaceId, eventType);
        assertThat(activityCount).isNotNull().isGreaterThan(0);
        assertThat(auditCount).isNotNull().isGreaterThan(0);
    }

    private boolean hasWorkerRun(JsonNode runs, String workerType, String status) {
        for (JsonNode run : runs) {
            if (workerType.equals(run.at("/workerType").asText()) && status.equals(run.at("/status").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWorkerHealth(JsonNode healthRows, String workerType, String status) {
        for (JsonNode health : healthRows) {
            if (workerType.equals(health.at("/workerType").asText()) && status.equals(health.at("/lastStatus").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean allWorkerRunsHaveType(JsonNode runs, String workerType) {
        for (JsonNode run : runs) {
            if (!workerType.equals(run.at("/workerType").asText())) {
                return false;
            }
        }
        return true;
    }

    private boolean allWorkerHealthRowsHaveType(JsonNode healthRows, String workerType) {
        for (JsonNode health : healthRows) {
            if (!workerType.equals(health.at("/workerType").asText())) {
                return false;
            }
        }
        return true;
    }

    private void createTypeTranslation(JsonNode mapping, String source, String target) throws Exception {
        postJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/type-translations", objectMapper.createObjectNode()
                .put("sourceTypeKey", source)
                .put("targetTypeKey", target)
                .put("enabled", true));
    }

    private void createStatusTranslation(JsonNode mapping, String source, String target) throws Exception {
        postJson("/api/v1/import-mapping-templates/" + mapping.at("/id").asText() + "/status-translations", objectMapper.createObjectNode()
                .put("sourceStatusKey", source)
                .put("targetStatusKey", target)
                .put("enabled", true));
    }

    private ArrayNode textArray(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private JsonNode postSetup() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        ObjectNode body = objectMapper.createObjectNode();
        body.set("adminUser", objectMapper.createObjectNode()
                .put("email", "import-sample-" + unique + "@example.com")
                .put("username", "import-sample-" + unique)
                .put("displayName", "Import Sample Admin")
                .put("password", "correct-horse-battery-staple"));
        body.set("organization", objectMapper.createObjectNode()
                .put("name", "Import Sample Organization")
                .put("slug", "import-sample-" + unique));
        body.set("workspace", objectMapper.createObjectNode()
                .put("name", "Import Sample Workspace")
                .put("key", "IS" + unique.substring(0, 6))
                .put("timezone", "America/Chicago")
                .put("locale", "en-US")
                .put("anonymousReadEnabled", true));
        body.set("project", objectMapper.createObjectNode()
                .put("name", "Import Sample Project")
                .put("key", "ISP" + unique.substring(0, 5))
                .put("description", "Project created by import sample integration test")
                .put("visibility", "public"));
        HttpResponse<String> response = rawPost("/api/v1/setup", body);
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private String login(JsonNode setup) throws Exception {
        HttpResponse<String> response = rawPost("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", setup.at("/adminUser/email").asText())
                .put("password", "correct-horse-battery-staple"));
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body()).at("/accessToken").asText();
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

    private UUID workItemTypeId(JsonNode setup, String key) {
        for (JsonNode type : setup.at("/seedData/workItemTypes")) {
            if (key.equals(type.at("/key").asText())) {
                return UUID.fromString(type.at("/id").asText());
            }
        }
        throw new IllegalStateException("Work item type not found: " + key);
    }

    private JsonNode findRecordBySourceId(JsonNode records, String sourceId) {
        for (JsonNode record : records) {
            if (sourceId.equals(record.at("/sourceId").asText())) {
                return record;
            }
        }
        throw new IllegalStateException("Record not found: " + sourceId);
    }

    private boolean hasDiffField(JsonNode fields, String path, String changeType) {
        for (JsonNode field : fields) {
            if (path.equals(field.at("/path").asText()) && changeType.equals(field.at("/changeType").asText())) {
                return true;
            }
        }
        return false;
    }

    private List<String> widgetTypes(JsonNode widgets) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
        for (JsonNode widget : widgets) {
            types.add(widget.at("/widgetType").asText());
        }
        return types;
    }

    private List<String> sampleKeys(JsonNode samples) {
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        for (JsonNode sample : samples) {
            keys.add(sample.at("/key").asText());
        }
        return keys;
    }

    private void setTextAtPath(ObjectNode root, String path, String value) {
        String[] segments = path.split("\\.");
        ObjectNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            current = (ObjectNode) current.get(segments[index]);
        }
        current.put(segments[segments.length - 1], value);
    }

    private record SampleSpec(
            String label,
            String provider,
            String sourceType,
            String path,
            String firstSourceId,
            String bugSourceId,
            String doneSourceId,
            String titlePath,
            String visibilityPath,
            String expectedFirstTitle,
            String expectedUpdatedTitle
    ) {
    }
}
