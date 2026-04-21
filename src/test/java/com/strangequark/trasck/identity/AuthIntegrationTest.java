package com.strangequark.trasck.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.event.DomainEvent;
import com.strangequark.trasck.event.ConfiguredDomainEventConsumer;
import com.strangequark.trasck.event.DomainEventConsumer;
import com.strangequark.trasck.event.EventConsumerConfig;
import com.strangequark.trasck.event.DomainEventOutboxDispatcher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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

    @Autowired
    private DomainEventOutboxDispatcher domainEventOutboxDispatcher;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("trasck.security.oauth-assertion-secret", () -> OAUTH_ASSERTION_SECRET);
        registry.add("trasck.events.outbox.fixed-delay-ms", () -> "600000");
    }

    @Test
    void supportsCookieAndBearerLoginUserManagementInvitationsOauthLinkingAndPermissionDenial() throws Exception {
        JsonNode setup = postSetup();
        UUID adminUserId = uuid(setup, "/adminUser/id");
        UUID workspaceId = uuid(setup, "/workspace/id");
        UUID projectId = uuid(setup, "/project/id");
        UUID viewerRoleId = roleId(setup, "viewer");
        UUID projectAdminRoleId = roleId(setup, "project_admin");

        assertThat(get("/api/v1/auth/me", null).statusCode()).isEqualTo(401);
        JsonNode openApi = read(get("/v3/api-docs", null));
        assertThat(openApi.at("/openapi").asText()).startsWith("3.");
        assertThat(openApi.at("/info/title").asText()).isEqualTo("Trasck API");
        HttpResponse<String> allowedPreflight = preflight("/api/v1/auth/me", "http://localhost:8080", "GET");
        assertThat(allowedPreflight.statusCode()).isEqualTo(200);
        assertThat(allowedPreflight.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:8080");
        HttpResponse<String> deniedPreflight = preflight("/api/v1/auth/me", "https://evil.example", "GET");
        assertThat(deniedPreflight.statusCode()).isEqualTo(403);

        String throttledIdentifier = "throttle-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 5; i++) {
            assertThat(loginWithForwardedFor(throttledIdentifier, "wrong-password", "198.51.100." + i).statusCode()).isEqualTo(401);
        }
        assertThat(loginWithForwardedFor(throttledIdentifier, "wrong-password", "198.51.100.250").statusCode()).isEqualTo(429);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from security_rate_limit_attempts where realm = 'login' and identifier = ?",
                Long.class,
                throttledIdentifier.toLowerCase()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from security_auth_failure_events where realm = 'login' and identifier = ?",
                Long.class,
                throttledIdentifier.toLowerCase()
        )).isEqualTo(5);

        AuthSession admin = login(setup.at("/adminUser/email").asText(), "correct-horse-battery-staple");
        assertThat(admin.accessToken()).isNotBlank();
        assertThat(admin.cookie()).contains("trasck_access_token=").contains("HttpOnly");

        JsonNode meByBearer = read(get("/api/v1/auth/me", admin.accessToken()));
        assertThat(uuid(meByBearer, "/id")).isEqualTo(adminUserId);

        HttpResponse<String> meByCookie = getWithCookie("/api/v1/auth/me", admin.cookie());
        assertThat(meByCookie.statusCode()).isEqualTo(200);

        HttpResponse<String> unsafeCookiePostWithoutCsrf = postWithCookies(
                "/api/v1/auth/tokens/personal",
                objectMapper.createObjectNode().put("name", "Browser token without CSRF"),
                null,
                null,
                admin.cookie()
        );
        assertThat(unsafeCookiePostWithoutCsrf.statusCode()).isEqualTo(403);

        HttpResponse<String> csrfResponse = getWithCookie("/api/v1/auth/csrf", admin.cookie());
        JsonNode csrf = read(csrfResponse);
        String csrfCookie = csrfResponse.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow();
        JsonNode personalToken = read(postWithCookies(
                "/api/v1/auth/tokens/personal",
                objectMapper.createObjectNode().put("name", "CLI token"),
                csrf.at("/headerName").asText(),
                csrf.at("/token").asText(),
                admin.cookie(),
                csrfCookie
        ));
        assertThat(personalToken.at("/token").asText()).startsWith("trpat_");
        assertThat(get("/api/v1/auth/me", personalToken.at("/token").asText()).statusCode()).isEqualTo(200);
        JsonNode listedPersonalTokens = read(get("/api/v1/auth/tokens/personal", admin.accessToken()));
        assertThat(listedPersonalTokens).hasSize(1);
        assertThat(listedPersonalTokens.get(0).at("/token").isNull()).isTrue();

        HttpResponse<String> openRegister = post("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", "no-invite@example.com")
                .put("username", "no-invite")
                .put("displayName", "No Invite")
                .put("password", "correct-horse-battery-staple"), null);
        assertThat(openRegister.statusCode()).isEqualTo(400);

        JsonNode revokedInvitation = read(post("/api/v1/workspaces/" + workspaceId + "/invitations", objectMapper.createObjectNode()
                .put("email", "revoked-invite@example.com"), admin.accessToken()));
        assertThat(delete("/api/v1/workspaces/" + workspaceId + "/invitations/" + uuid(revokedInvitation, "/id"), admin.accessToken()).statusCode()).isEqualTo(204);
        JsonNode allInvitations = read(get("/api/v1/workspaces/" + workspaceId + "/invitations?status=all", admin.accessToken()));
        assertThat(allInvitations).anySatisfy(listed -> {
            assertThat(uuid((JsonNode) listed, "/id")).isEqualTo(uuid(revokedInvitation, "/id"));
            assertThat(((JsonNode) listed).has("token")).isFalse();
            assertThat(((JsonNode) listed).has("tokenHash")).isFalse();
        });
        assertThat(jdbcTemplate.queryForObject(
                "select status from user_invitations where id = ?",
                String.class,
                uuid(revokedInvitation, "/id")
        )).isEqualTo("revoked");
        HttpResponse<String> revokedRegister = post("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", "revoked-invite@example.com")
                .put("username", "revoked-invite")
                .put("displayName", "Revoked Invite")
                .put("password", "correct-horse-battery-staple")
                .put("invitationToken", revokedInvitation.at("/token").asText()), null);
        assertThat(revokedRegister.statusCode()).isEqualTo(403);

        JsonNode invitation = read(post("/api/v1/workspaces/" + workspaceId + "/invitations", objectMapper.createObjectNode()
                .put("email", "invited@example.com"), admin.accessToken()));
        assertThat(invitation.at("/token").asText()).isNotBlank();
        JsonNode pendingInvitations = read(get("/api/v1/workspaces/" + workspaceId + "/invitations", admin.accessToken()));
        assertThat(pendingInvitations).anySatisfy(listed -> {
            assertThat(uuid((JsonNode) listed, "/id")).isEqualTo(uuid(invitation, "/id"));
            assertThat(((JsonNode) listed).at("/email").asText()).isEqualTo("invited@example.com");
            assertThat(((JsonNode) listed).at("/status").asText()).isEqualTo("pending");
            assertThat(((JsonNode) listed).has("token")).isFalse();
        });

        JsonNode registered = read(post("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", "invited@example.com")
                .put("username", "invited-user")
                .put("displayName", "Invited User")
                .put("password", "correct-horse-battery-staple")
                .put("invitationToken", invitation.at("/token").asText()), null));
        assertThat(registered.at("/accessToken").asText()).isNotBlank();
        assertThat(countWhere("workspace_memberships", "user_id", uuid(registered, "/user/id"))).isEqualTo(1);

        JsonNode projectInvitation = read(post("/api/v1/workspaces/" + workspaceId + "/invitations", objectMapper.createObjectNode()
                .put("email", "project-invited@example.com")
                .put("projectId", projectId.toString())
                .put("projectRoleId", projectAdminRoleId.toString()), admin.accessToken()));
        assertThat(projectInvitation.at("/projectId").asText()).isEqualTo(projectId.toString());

        JsonNode projectRegistered = read(post("/api/v1/auth/register", objectMapper.createObjectNode()
                .put("email", "project-invited@example.com")
                .put("username", "project-invited-user")
                .put("displayName", "Project Invited User")
                .put("password", "correct-horse-battery-staple")
                .put("invitationToken", projectInvitation.at("/token").asText()), null));
        assertThat(countWhere("project_memberships", "user_id", uuid(projectRegistered, "/user/id"))).isEqualTo(1);

        JsonNode viewer = read(post("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", "viewer@example.com")
                .put("username", "viewer-user")
                .put("displayName", "Viewer User")
                .put("password", "correct-horse-battery-staple")
                .put("roleId", viewerRoleId.toString()), admin.accessToken()));
        assertThat(uuid(viewer, "/id")).isNotNull();
        JsonNode workspaceUsers = read(get("/api/v1/workspaces/" + workspaceId + "/users", admin.accessToken()));
        assertThat(workspaceUsers).anySatisfy(listed -> {
            assertThat(uuid((JsonNode) listed, "/userId")).isEqualTo(uuid(viewer, "/id"));
            assertThat(((JsonNode) listed).at("/email").asText()).isEqualTo("viewer@example.com");
            assertThat(((JsonNode) listed).at("/roleKey").asText()).isEqualTo("viewer");
            assertThat(((JsonNode) listed).has("passwordHash")).isFalse();
        });

        AuthSession viewerSession = login("viewer@example.com", "correct-horse-battery-staple");
        assertThat(get("/api/v1/projects/" + projectId + "/work-items", viewerSession.accessToken()).statusCode()).isEqualTo(200);
        assertThat(delete("/api/v1/workspaces/" + workspaceId + "/users/" + adminUserId, admin.accessToken()).statusCode()).isEqualTo(409);
        JsonNode removableUser = read(post("/api/v1/workspaces/" + workspaceId + "/users", objectMapper.createObjectNode()
                .put("email", "removable@example.com")
                .put("username", "removable-user")
                .put("displayName", "Removable User")
                .put("password", "correct-horse-battery-staple")
                .put("roleId", viewerRoleId.toString()), admin.accessToken()));
        assertThat(delete("/api/v1/workspaces/" + workspaceId + "/users/" + uuid(removableUser, "/id"), admin.accessToken()).statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "select status from workspace_memberships where workspace_id = ? and user_id = ?",
                String.class,
                workspaceId,
                uuid(removableUser, "/id")
        )).isEqualTo("removed");
        HttpResponse<String> removedLogin = post("/api/v1/auth/login", objectMapper.createObjectNode()
                .put("identifier", "removable@example.com")
                .put("password", "correct-horse-battery-staple"), null);
        assertThat(removedLogin.statusCode()).isEqualTo(401);
        HttpResponse<String> forbiddenCreate = post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Viewer should not create"), viewerSession.accessToken());
        assertThat(forbiddenCreate.statusCode()).isEqualTo(403);

        JsonNode watchedWorkItem = read(post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Viewer watcher self-service")
                .put("reporterId", adminUserId.toString()), admin.accessToken()));
        UUID watchedWorkItemId = uuid(watchedWorkItem, "/id");
        JsonNode viewerWatcher = read(post("/api/v1/work-items/" + watchedWorkItemId + "/watchers", objectMapper.createObjectNode(), viewerSession.accessToken()));
        assertThat(uuid(viewerWatcher, "/userId")).isEqualTo(uuid(viewer, "/id"));
        HttpResponse<String> forbiddenOtherWatcher = post("/api/v1/work-items/" + watchedWorkItemId + "/watchers", objectMapper.createObjectNode()
                .put("userId", adminUserId.toString()), viewerSession.accessToken());
        assertThat(forbiddenOtherWatcher.statusCode()).isEqualTo(403);
        assertThat(delete("/api/v1/work-items/" + watchedWorkItemId + "/watchers/" + uuid(viewer, "/id"), viewerSession.accessToken()).statusCode()).isEqualTo(204);

        JsonNode serviceToken = read(post("/api/v1/workspaces/" + workspaceId + "/service-tokens", objectMapper.createObjectNode()
                .put("name", "Automation reader")
                .put("username", "automation-reader")
                .put("displayName", "Automation Reader")
                .put("roleId", viewerRoleId.toString()), admin.accessToken()));
        assertThat(serviceToken.at("/token").asText()).startsWith("trsvc_");
        assertThat(get("/api/v1/projects/" + projectId + "/work-items", serviceToken.at("/token").asText()).statusCode()).isEqualTo(200);
        JsonNode listedServiceTokens = read(get("/api/v1/workspaces/" + workspaceId + "/service-tokens", admin.accessToken()));
        assertThat(listedServiceTokens).hasSize(1);
        assertThat(listedServiceTokens.get(0).at("/token").isNull()).isTrue();
        JsonNode usersAfterServiceToken = read(get("/api/v1/workspaces/" + workspaceId + "/users?status=all", admin.accessToken()));
        assertThat(usersAfterServiceToken).noneSatisfy(listed ->
                assertThat(uuid((JsonNode) listed, "/userId")).isEqualTo(uuid(serviceToken, "/userId")));

        JsonNode expiredServiceToken = read(post("/api/v1/workspaces/" + workspaceId + "/service-tokens", objectMapper.createObjectNode()
                .put("name", "Expired automation reader")
                .put("username", "expired-automation-reader")
                .put("displayName", "Expired Automation Reader")
                .put("roleId", viewerRoleId.toString())
                .put("expiresAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString()), admin.accessToken()));
        assertThat(get("/api/v1/projects/" + projectId + "/work-items", expiredServiceToken.at("/token").asText()).statusCode()).isEqualTo(401);

        JsonNode readOnlyToken = read(post("/api/v1/auth/tokens/personal", objectMapper.createObjectNode()
                .put("name", "Read-only work item token")
                .set("scopes", objectMapper.createArrayNode().add("work_item.read")), admin.accessToken()));
        assertThat(get("/api/v1/projects/" + projectId + "/work-items", readOnlyToken.at("/token").asText()).statusCode()).isEqualTo(200);
        HttpResponse<String> scopeDeniedCreate = post("/api/v1/projects/" + projectId + "/work-items", objectMapper.createObjectNode()
                .put("typeKey", "story")
                .put("title", "Token scope should block create"), readOnlyToken.at("/token").asText());
        assertThat(scopeDeniedCreate.statusCode()).isEqualTo(403);
        JsonNode expiredPersonalToken = read(post("/api/v1/auth/tokens/personal", objectMapper.createObjectNode()
                .put("name", "Expired personal token")
                .put("expiresAt", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString()), admin.accessToken()));
        assertThat(get("/api/v1/auth/me", expiredPersonalToken.at("/token").asText()).statusCode()).isEqualTo(401);
        assertThat(delete("/api/v1/auth/tokens/" + uuid(readOnlyToken, "/id"), admin.accessToken()).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/projects/" + projectId + "/work-items", readOnlyToken.at("/token").asText()).statusCode()).isEqualTo(401);

        HttpResponse<String> oauthRedirect = get("/api/v1/auth/oauth2/authorization/github", null);
        assertThat(oauthRedirect.statusCode()).isBetween(300, 399);
        assertThat(oauthRedirect.headers().firstValue("Location").orElse(""))
                .contains("github.com/login/oauth/authorize");

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
        assertThat(countWhere("domain_events", "processing_status", "published")).isEqualTo(0);

        jdbcTemplate.update("""
                insert into event_consumer_configs (consumer_key, consumer_type, display_name, event_types, config)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                """, "configured-auth-test", "test-configured", "Configured Auth Test", "[\"auth.oauth_identity_linked\"]", "{}");
        domainEventOutboxDispatcher.dispatchPending();
        assertThat(countWhere("domain_event_deliveries", "consumer_key", "auth-integration-test")).isGreaterThan(0);
        assertThat(countWhere("domain_event_deliveries", "consumer_key", "configured-auth-test")).isEqualTo(1);
        assertThat(countWhere("domain_events", "processing_status", "published")).isGreaterThan(0);

        UUID retryEventId = insertDomainEvent(workspaceId, "outbox.retry_test", adminUserId);
        jdbcTemplate.update("""
                insert into event_consumer_configs (consumer_key, consumer_type, display_name, event_types, config)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                """, "configured-retry-test", "test-configured", "Configured Retry Test", "[\"outbox.retry_test\"]", "{\"fail\":true}");
        domainEventOutboxDispatcher.dispatchPending();
        assertDelivery(retryEventId, "configured-retry-test", "failed", 1);
        jdbcTemplate.update("""
                update domain_event_deliveries
                set next_attempt_at = now() - interval '1 minute'
                where domain_event_id = ? and consumer_key = ?
                """, retryEventId, "configured-retry-test");
        domainEventOutboxDispatcher.dispatchPending();
        assertDelivery(retryEventId, "configured-retry-test", "failed", 2);

        UUID deadLetterEventId = insertDomainEvent(workspaceId, "outbox.deadletter_test", adminUserId);
        jdbcTemplate.update("""
                insert into event_consumer_configs (consumer_key, consumer_type, display_name, event_types, config)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                """, "configured-deadletter-test", "test-configured", "Configured Dead Letter Test", "[\"outbox.deadletter_test\"]", "{\"fail\":true,\"maxAttempts\":1,\"deadLetterOnExhaustion\":true}");
        domainEventOutboxDispatcher.dispatchPending();
        assertDelivery(deadLetterEventId, "configured-deadletter-test", "dead_lettered", 1);
        assertThat(eventStatus(deadLetterEventId)).isEqualTo("dead_lettered");

        UUID disabledEventId = insertDomainEvent(workspaceId, "outbox.disabled_test", adminUserId);
        jdbcTemplate.update("""
                insert into event_consumer_configs (consumer_key, consumer_type, display_name, event_types, config, enabled)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), false)
                """, "configured-disabled-test", "test-configured", "Configured Disabled Test", "[\"outbox.disabled_test\"]", "{}");
        domainEventOutboxDispatcher.dispatchPending();
        assertThat(deliveryCount(disabledEventId, "configured-disabled-test")).isZero();

        UUID multiConsumerEventId = insertDomainEvent(workspaceId, "outbox.multi_test", adminUserId);
        jdbcTemplate.update("""
                insert into event_consumer_configs (consumer_key, consumer_type, display_name, event_types, config)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb))
                """, "configured-multi-test", "test-configured", "Configured Multi Test", "[\"outbox.multi_test\"]", "{}");
        domainEventOutboxDispatcher.dispatchPending();
        assertThat(deliveredCount(multiConsumerEventId)).isGreaterThanOrEqualTo(2);
        assertDelivery(multiConsumerEventId, "auth-integration-test", "delivered", 1);
        assertDelivery(multiConsumerEventId, "configured-multi-test", "delivered", 1);

        ObjectNode unknownReplayRequest = objectMapper.createObjectNode().put("includePublished", true);
        unknownReplayRequest.set("eventIds", objectMapper.createArrayNode().add(disabledEventId.toString()));
        unknownReplayRequest.set("consumerKeys", objectMapper.createArrayNode().add("missing-consumer"));
        JsonNode unknownReplay = read(post("/api/v1/workspaces/" + workspaceId + "/domain-events/replay", unknownReplayRequest, admin.accessToken()));
        assertThat(unknownReplay.at("/eventsMatched").asInt()).isEqualTo(1);
        assertThat(unknownReplay.at("/deliveriesReset").asInt()).isZero();

        JsonNode defaultRetention = read(get("/api/v1/workspaces/" + workspaceId + "/audit-retention-policy", admin.accessToken()));
        assertThat(defaultRetention.at("/retentionEnabled").asBoolean()).isFalse();
        JsonNode retention = read(put("/api/v1/workspaces/" + workspaceId + "/audit-retention-policy", objectMapper.createObjectNode()
                .put("retentionEnabled", true)
                .put("retentionDays", 365), admin.accessToken()));
        assertThat(retention.at("/retentionEnabled").asBoolean()).isTrue();
        assertThat(retention.at("/retentionDays").asInt()).isEqualTo(365);
        UUID oldAuditId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into audit_log_entries (id, workspace_id, action, target_type, target_id, created_at)
                values (?, ?, ?, ?, ?, now() - interval '400 days')
                """, oldAuditId, workspaceId, "audit.old_retention_test", "workspace", workspaceId);
        JsonNode retentionExport = read(post("/api/v1/workspaces/" + workspaceId + "/audit-retention-policy/export?limit=10", objectMapper.createObjectNode(), admin.accessToken()));
        assertThat(retentionExport.at("/entriesEligible").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(retentionExport.at("/exportJobId").asText()).isNotBlank();
        assertThat(retentionExport.at("/fileAttachmentId").asText()).isNotBlank();
        assertThat(retentionExport.at("/filename").asText()).startsWith("audit-retention-");
        assertThat(retentionExport.at("/storageKey").asText()).contains("audit-retention-");
        assertThat(retentionExport.at("/checksum").asText()).startsWith("sha256:");
        assertThat(retentionExport.at("/sizeBytes").asLong()).isGreaterThan(0);
        UUID exportAttachmentId = uuid(retentionExport, "/fileAttachmentId");
        assertThat(countWhere("attachments", "id", exportAttachmentId)).isEqualTo(1);
        assertThat(countWhere("export_jobs", "file_attachment_id", exportAttachmentId)).isEqualTo(1);
        assertThat(ids(retentionExport.at("/entries"))).contains(oldAuditId.toString());
        JsonNode retentionPrune = read(post("/api/v1/workspaces/" + workspaceId + "/audit-retention-policy/prune", objectMapper.createObjectNode(), admin.accessToken()));
        assertThat(retentionPrune.at("/entriesPruned").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(retentionPrune.at("/exportJobId").asText()).isNotBlank();
        UUID pruneAttachmentId = uuid(retentionPrune, "/fileAttachmentId");
        assertThat(countWhere("attachments", "id", pruneAttachmentId)).isEqualTo(1);
        assertThat(countWhere("export_jobs", "file_attachment_id", pruneAttachmentId)).isEqualTo(1);
        UUID pruneExportJobId = uuid(retentionPrune, "/exportJobId");
        JsonNode exportJobs = read(get("/api/v1/workspaces/" + workspaceId + "/export-jobs?exportType=audit_retention&limit=10", admin.accessToken()));
        assertThat(exportJobs.at("/items")).isNotEmpty();
        JsonNode pruneExportJob = read(get("/api/v1/workspaces/" + workspaceId + "/export-jobs/" + pruneExportJobId, admin.accessToken()));
        assertThat(uuid(pruneExportJob, "/fileAttachmentId")).isEqualTo(pruneAttachmentId);
        HttpResponse<String> downloadedExport = get("/api/v1/workspaces/" + workspaceId + "/export-jobs/" + pruneExportJobId + "/download", admin.accessToken());
        assertThat(downloadedExport.statusCode()).isEqualTo(200);
        assertThat(downloadedExport.headers().firstValue("Content-Disposition")).hasValueSatisfying(value -> assertThat(value).contains("audit-retention-"));
        assertThat(downloadedExport.body()).contains(oldAuditId.toString(), "audit.old_retention_test");
        assertThat(countWhere("audit_log_entries", "id", oldAuditId)).isZero();

        UUID secretEventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into domain_events (id, workspace_id, aggregate_type, aggregate_id, event_type, payload)
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                """,
                secretEventId,
                workspaceId,
                "user",
                adminUserId,
                "auth.user_created",
                "{\"actorUserId\":\"" + adminUserId + "\",\"userId\":\"" + adminUserId + "\",\"secret\":\"super-secret\",\"token\":\"raw-token\",\"safe\":\"visible\"}"
        );
        domainEventOutboxDispatcher.dispatchPending();
        JsonNode auditLog = read(get("/api/v1/workspaces/" + workspaceId + "/audit-log?limit=100", admin.accessToken()));
        assertThat(actions(auditLog)).contains("audit.retention_policy_updated", "auth.service_token_created", "auth.user_created");
        assertThat(auditLog.toString()).contains("[REDACTED]").doesNotContain("super-secret").doesNotContain("raw-token");

        ObjectNode replayRequest = objectMapper.createObjectNode().put("includePublished", true);
        replayRequest.set("consumerKeys", objectMapper.createArrayNode().add("audit-projection"));
        JsonNode replay = read(post("/api/v1/workspaces/" + workspaceId + "/domain-events/replay", replayRequest, admin.accessToken()));
        assertThat(replay.at("/deliveriesReset").asInt()).isGreaterThan(0);
        domainEventOutboxDispatcher.dispatchPending();
        assertThat(countAuditForDomainEvent(secretEventId)).isEqualTo(1);
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

    private HttpResponse<String> loginWithForwardedFor(String identifier, String password, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(objectMapper.createObjectNode()
                        .put("identifier", identifier)
                        .put("password", password))))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, JsonNode body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, JsonNode body, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String path, String origin, String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String accessToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).DELETE();
        authorize(builder, accessToken);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithCookie(String path, String setCookieHeader) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Cookie", cookieHeader(setCookieHeader))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithCookies(
            String path,
            JsonNode body,
            String csrfHeaderName,
            String csrfToken,
            String... setCookieHeaders
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Cookie", cookieHeader(setCookieHeaders))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (csrfHeaderName != null && csrfToken != null) {
            builder.header(csrfHeaderName, csrfToken);
        }
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

    private String cookieHeader(String... setCookieHeaders) {
        return Arrays.stream(setCookieHeaders)
                .map(setCookieHeader -> setCookieHeader.substring(0, setCookieHeader.indexOf(';')))
                .collect(Collectors.joining("; "));
    }

    private UUID uuid(JsonNode node, String pointer) {
        return UUID.fromString(node.at(pointer).asText());
    }

    private String[] actions(JsonNode entries) {
        JsonNode pageItems = entries.has("items") ? entries.at("/items") : entries;
        String[] actions = new String[pageItems.size()];
        for (int i = 0; i < pageItems.size(); i++) {
            actions[i] = pageItems.get(i).at("/action").asText();
        }
        return actions;
    }

    private String[] ids(JsonNode entries) {
        JsonNode pageItems = entries.has("items") ? entries.at("/items") : entries;
        String[] ids = new String[pageItems.size()];
        for (int i = 0; i < pageItems.size(); i++) {
            ids[i] = pageItems.get(i).at("/id").asText();
        }
        return ids;
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

    private int countAuditForDomainEvent(UUID domainEventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_log_entries where domain_event_id = ?",
                Integer.class,
                domainEventId
        );
        return count == null ? 0 : count;
    }

    private UUID insertDomainEvent(UUID workspaceId, String eventType, UUID actorId) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into domain_events (id, workspace_id, aggregate_type, aggregate_id, event_type, payload)
                values (?, ?, ?, ?, ?, cast(? as jsonb))
                """,
                eventId,
                workspaceId,
                "workspace",
                workspaceId,
                eventType,
                "{\"actorUserId\":\"" + actorId + "\"}"
        );
        return eventId;
    }

    private void assertDelivery(UUID domainEventId, String consumerKey, String status, int attempts) {
        assertThat(jdbcTemplate.queryForObject("""
                select delivery_status
                from domain_event_deliveries
                where domain_event_id = ? and consumer_key = ?
                """, String.class, domainEventId, consumerKey)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("""
                select attempts
                from domain_event_deliveries
                where domain_event_id = ? and consumer_key = ?
                """, Integer.class, domainEventId, consumerKey)).isEqualTo(attempts);
    }

    private int deliveryCount(UUID domainEventId, String consumerKey) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from domain_event_deliveries
                where domain_event_id = ? and consumer_key = ?
                """, Integer.class, domainEventId, consumerKey);
        return count == null ? 0 : count;
    }

    private int deliveredCount(UUID domainEventId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from domain_event_deliveries
                where domain_event_id = ? and delivery_status = 'delivered'
                """, Integer.class, domainEventId);
        return count == null ? 0 : count;
    }

    private String eventStatus(UUID domainEventId) {
        return jdbcTemplate.queryForObject("select processing_status from domain_events where id = ?", String.class, domainEventId);
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

    @TestConfiguration
    static class DomainEventConsumerConfig {
        @Bean
        DomainEventConsumer authIntegrationDomainEventConsumer() {
            return new DomainEventConsumer() {
                @Override
                public String consumerKey() {
                    return "auth-integration-test";
                }

                @Override
                public void handle(DomainEvent event) {
                }
            };
        }

        @Bean
        ConfiguredDomainEventConsumer configuredAuthIntegrationDomainEventConsumer() {
            return new ConfiguredDomainEventConsumer() {
                @Override
                public String consumerType() {
                    return "test-configured";
                }

                @Override
                public void handle(DomainEvent event, EventConsumerConfig config) {
                    if (config.getConfig() != null && config.getConfig().path("fail").asBoolean(false)) {
                        throw new IllegalStateException("configured test consumer failure");
                    }
                }
            };
        }
    }
}
