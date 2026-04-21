# Trasck Security Audit

Date: 2026-04-21

## Scope

This audit covers the current Trasck backend repo and Vite React frontend repo before broader manual product testing.

- Backend tracked and pending source files reviewed: 696
- Frontend tracked and pending source files reviewed: 86
- Excluded from scope: `.git`, `target`, `node_modules`, `dist`, generated runtime data, and other build output directories.
- Included in scope: Java source, React source, generated OpenAPI/client artifacts, Flyway migrations, tests, Docker/nginx/build config, Jenkinsfiles, checked-in `.env.example` files, local runtime docs, HTTP examples, and import samples.

The file pass was done with full-path inventory review, targeted secret and dangerous API scans, dependency/build inspection, and focused source review of the security-sensitive systems listed below. No product subsystem was intentionally skipped.

## Method

Commands and inspections used during this pass:

- `git ls-files`, `find`, and `rg --files` to enumerate backend and frontend files.
- Secret scans for `password`, `secret`, `token`, `api_key`, `private_key`, authorization headers, cookies, and key material.
- Backend auth scans for `permitAll`, CSRF, CORS, cookies, OAuth, JWT, Swagger/OpenAPI, public routes, and bearer-token paths.
- Frontend browser-risk scans for `localStorage`, `sessionStorage`, `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function`, `document.cookie`, bearer tokens, and API-base handling.
- SQL and query scans for native SQL, string-built query fragments, sort/order handling, and raw-SQL escape hatches.
- URL, webhook, storage, and file scans for outbound HTTP, URI construction, local path handling, multipart uploads, attachment storage, and delete/read/write operations.
- Port scan for `6100` and `6101`; no non-documentation `6101` runtime/config references were found.
- `npm audit --audit-level=high` in the frontend returned zero reported vulnerabilities.
- Java dependency vulnerability automation is wired through the OWASP Dependency Check Maven profile and backend CI workflow.
- Follow-up hardening verification ran the targeted security/auth tests, the full backend Maven test suite, frontend Vitest suite, frontend production build, frontend `npm audit`, `.env` inventory, `6101` config scan, whitespace checks, and local listener/container checks.

## Threat Model

Primary assets:

- User accounts, password hashes, OAuth identities, invitations, JWTs, personal tokens, service tokens, and CSRF tokens.
- Organization, workspace, project, team, work item, comment, attachment, import, export, report, dashboard, notification, automation, webhook, email, audit, and agent records.
- Encrypted provider credentials, callback private keys, SMTP passwords, object-storage credentials, webhook secrets, and worker tokens.
- Import/export artifacts and attachment bytes in local or S3-compatible storage.

Main trust boundaries:

- Browser to backend API, using HTTP-only auth cookies plus CSRF or direct bearer credentials.
- Direct API clients to backend API with JWT, personal token, or service token.
- Backend to PostgreSQL.
- Backend to attachment storage providers.
- Backend to OAuth providers, SMTP servers, webhooks, S3-compatible endpoints, and AI agent worker/callback endpoints.
- Backend to frontend generated OpenAPI/client contract.
- Public anonymous reads to explicitly public project APIs.

Highest-risk entry points:

- Auth setup/login/register/OAuth/token endpoints.
- Public project reads.
- Attachment upload/download/delete.
- Import parsing/materialization/export/download.
- Automation, webhook, email, and worker processing endpoints.
- Agent provider credentials, dispatch attempts, callbacks, generic worker protocol, and webhook-push dispatch.
- Audit/export retention and replay endpoints.
- Generated OpenAPI and Swagger UI.

## Observed Controls

- Most APIs require authentication through Spring Security; domain services enforce workspace/project permissions for high-value data.
- Browser sessions use an HTTP-only `trasck_access_token` cookie. Unsafe cookie-authenticated requests require a CSRF token.
- Direct API clients may use bearer JWTs, personal tokens, or service tokens; token scopes are enforced in addition to role permissions.
- Personal and service API tokens store hashes only, are revocable, and return the raw token only on creation.
- Agent provider credentials and callback private keys are encrypted at rest with AES-GCM through `SecretCipherService`.
- Callback JWTs are provider scoped and can be invalidated immediately by callback key rotation.
- Generic worker endpoints require active encrypted worker-token credentials with `metadata.workerId`.
- Activity/audit payload redaction covers token, password, secret, credential, authorization, cookie, hash, and private-key shaped fields.
- Production-like profiles fail startup when known development secrets, weak database passwords, insecure auth-cookie flags, local OAuth success redirects, local/wildcard CORS origins, non-Redis rate-limit stores, or unreachable Redis-backed throttling are active.
- OpenAPI and Swagger stay public for local/non-production contract work, but production-like profiles require authenticated dedicated system-admin access.
- CORS is explicitly allowlist-based through `CORS_ALLOWED_ORIGINS`.
- Configured outbound HTTP targets are protected by a shared default-deny URL policy for localhost, private, link-local, carrier-grade NAT, unique-local IPv6, multicast, and metadata-like local destinations unless exact hosts, wildcard hosts, or CIDR ranges are allowlisted.
- Human password creation now enforces a minimum length and rejects common development passwords; login failures are throttled by identifier plus remote address using database-backed counters by default or Redis-backed counters when enabled, production-like profiles require Redis-backed throttling and ping Redis on startup, and `X-Forwarded-For` is ignored unless explicitly trusted behind a controlled proxy.
- Login, agent callback, and generic worker authentication failures write durable security telemetry rows.
- Attachment metadata/upload/download, import parser payloads, and generated export artifacts enforce effective deployment/workspace/project byte limits and allowlisted content types. Anonymous public attachment downloads use short-lived app-signed backend URLs and re-check public visibility plus download policy before streaming bytes.
- API error bodies and production server-error logs pass through centralized redaction for token/password/secret/authorization/private-key shaped text.
- Saved-filter/query-builder SQL uses whitelisted fields/operators and bind parameters. Raw SQL-shaped keys are rejected in user-configurable report/filter/dashboard paths.
- Local filesystem attachment storage normalizes paths and rejects storage keys that escape the configured root.
- The frontend does not persist access tokens in local storage. Current local storage is limited to API base URL and selected UI/entity IDs.
- No `dangerouslySetInnerHTML`, `eval`, `new Function`, or direct `document.cookie` usage was found in current frontend source.
- No `6101` backend or frontend config references were found; current backend defaults remain on `6100`.
- Only `.env.example` files remain in the two repos; real `.env` files are ignored.
- Frontend container builds use `npm ci`; nginx now sends baseline security headers and uses runtime-templated CSP directives.

## Findings

### SEC-001: Checked-in secret-bearing local environment files

Severity: High before public hosting

Status: Resolved in first hardening pass; CI secret scanning remains future work.

Affected files:

- `.env`
- `../trasck-frontend/.env`
- `.gitignore`
- `../trasck-frontend/.gitignore`

Current state:

- Both repos now ignore `.env` and `.env.*`.
- Both repos commit only `.env.example`.
- The stale frontend `6110` fallback is gone; the frontend example points at `http://localhost:6100`.

Impact:

- The current values appear local-only, but tracking `.env` establishes an unsafe pattern and could lead to real production secrets being committed later.

Recommended fix:

- Keep real `.env` files untracked.
- Add a CI secret scan that prevents future secret regressions.

Regression tests/checks:

- Add a CI secret scan that fails on real-looking private keys, JWT secrets, SMTP passwords, OAuth secrets, object-storage keys, provider credentials, worker tokens, and callback private keys.

### SEC-002: Production runtime can start with development secrets and passwords

Severity: High before public hosting

Status: Resolved for known development defaults in production-like profiles.

Affected files:

- `src/main/resources/application.properties`
- `docker-compose.yml`
- `.env`
- `docs/LOCAL_RUNTIME.md`

Current state:

- Local/default profiles still have developer-friendly defaults.
- Production-like `prod`, `production`, `staging`, and `hosted` profiles fail startup if development JWT/encryption/OAuth assertion secrets, weak database passwords, insecure cookie flags, local OAuth redirects, or local/wildcard CORS origins are active.
- OAuth provider secrets are required when a real provider client ID is configured.

Impact:

- A misconfigured production deployment could share known signing/encryption secrets, use weak database credentials, or set auth cookies without the `Secure` flag.

Recommended fix:

- Keep expanding production startup checks as deployment packaging, SMTP mode, object storage, and external worker profiles become concrete.

Regression tests/checks:

- Add profile-specific startup tests for allowed local defaults and rejected production defaults.

### SEC-003: Swagger/OpenAPI are public in every runtime profile

Severity: High before public hosting

Status: Resolved for the current decision to keep production docs behind dedicated system-admin access.

Affected file:

- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`

Current state:

- `GET /v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` remain public only in non-production-like profiles.
- Production-like profiles require an authenticated principal with `system.admin` scope and an active `system_admins` row.

Impact:

- Public production deployments would expose the full API surface, schemas, endpoint names, and internal workflow shape to unauthenticated users.

Recommended fix:

- Add system-admin management APIs/screens before hosted multi-tenant deployment so this access is not managed manually in the database.

Regression tests/checks:

- Add security tests proving docs are public in local/test only and require active system-admin access under production-like profiles.

### SEC-004: CORS configuration property is defined but not enforced

Severity: Medium-high

Status: Resolved for current backend browser entry points.

Affected files:

- `src/main/resources/application.properties`
- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`

Current state:

- `cors.allowed-origins` is enforced through a `CorsConfigurationSource`.
- Credentialed browser requests are limited to configured origins.
- Tests cover allowed localhost preflight and denied unlisted origins.

Impact:

- Browser behavior depends on framework defaults instead of an explicit allowlist. This can break legitimate browser use or create accidental exposure if future config changes enable broad CORS.

Recommended fix:

- Keep production CORS origins explicit and non-local through profile startup validation.

Regression tests/checks:

- Add CORS integration tests for preflight and credentialed requests.

### SEC-005: Login and password policy are too weak for production

Severity: High before public hosting

Status: Resolved for local database-backed counters and production-like Redis-backed counters; production packaging still needs Redis health/readiness observability.

Affected files:

- `src/main/java/com/strangequark/trasck/identity/AuthService.java`
- `src/main/java/com/strangequark/trasck/setup/SetupRequestValidator.java`

Current state:

- Login failures are throttled by identifier plus remote address using configurable database-backed counters by default or Redis-backed counters when `TRASCK_SECURITY_RATE_LIMIT_STORE=redis`, with configurable failure window and lockout duration; production-like startup verifies that Redis is reachable.
- Production-like `prod`, `production`, `staging`, and `hosted` profiles fail startup unless `TRASCK_SECURITY_RATE_LIMIT_STORE=redis` and the configured Redis connection responds to startup ping.
- Spoofable `X-Forwarded-For` values are ignored by default and only used when `TRASCK_TRUST_FORWARDED_FOR=true` is set behind a trusted reverse proxy.
- New human passwords are validated for configured minimum length and common development password rejection during setup, invitation registration, and workspace-admin direct user creation.
- Auth failure events are persisted for login failures, agent callback failures, and generic worker token failures.

Impact:

- Password guessing and weak account passwords are practical once exposed to the internet.

Recommended fix:

- Add deployment health checks and operations visibility for Redis-backed throttling after startup so hosted traffic can be drained if Redis becomes unhealthy.
- Consider stronger entropy checks once password reset/change flows exist.

Regression tests/checks:

- Keep tests for weak password rejection, login throttling/lockout boundaries, database and Redis counter paths, durable failure telemetry, and trusted-proxy forwarding behavior.

### SEC-006: Outbound HTTP targets need SSRF and egress controls

Severity: High before public hosting

Status: Resolved as a baseline default-deny policy with exact host, host:port, wildcard host, wildcard host:port, and CIDR allowlisting.

Affected files:

- `src/main/java/com/strangequark/trasck/automation/AutomationService.java`
- `src/main/java/com/strangequark/trasck/agent/AgentWorkerWebhookDispatchConsumer.java`
- `src/main/java/com/strangequark/trasck/activity/storage/S3CompatibleAttachmentStorageProvider.java`
- `src/main/java/com/strangequark/trasck/agent/AgentService.java`

Current state:

- A shared outbound URL policy validates webhooks, agent worker callback URLs, OAuth verified-email side-fetch URLs, and S3-compatible endpoints.
- The policy default-denies localhost, private networks, link-local ranges, carrier-grade NAT, unique-local IPv6, multicast, and metadata-like local destinations unless exact hosts, wildcard hosts, or CIDR ranges are explicitly allowlisted.
- Outbound webhook, agent-worker, OAuth side-fetch, and S3-compatible calls now use bounded connect/request timeouts.

Impact:

- A workspace admin or compromised admin account could configure calls to internal services, cloud metadata endpoints, or private network resources.

Recommended fix:

- Keep allowlists narrow and deployment-owned; review whether workspace-admin-managed egress allowlists are ever needed.

Regression tests/checks:

- Keep validation tests for denied localhost/private/link-local URLs, wildcard host allowlists, CIDR allowlists, and allowed external HTTPS URLs.

### SEC-007: Auth cookies and OAuth redirects need production guardrails

Severity: Medium-high

Status: Partially resolved by production startup validation.

Affected files:

- `src/main/java/com/strangequark/trasck/identity/AuthController.java`
- `src/main/java/com/strangequark/trasck/identity/OAuth2LoginSuccessHandler.java`
- `src/main/java/com/strangequark/trasck/config/OAuthClientConfig.java`

Current state:

- Auth cookies are `HttpOnly`, `SameSite=Lax`, and optionally `Secure`, but `Secure` defaults to false.
- OAuth success redirect defaults to localhost.
- OAuth client registrations use disabled placeholder credentials when not configured.
- Production-like profiles now fail startup when secure cookies are disabled, OAuth success redirect remains local, or enabled provider client IDs are missing matching non-development secrets.

Impact:

- A production misconfiguration could send insecure cookies or redirect users to the wrong location.

Recommended fix:

- Require secure cookies and explicit success redirect origins in production-like profiles.
- Add redirect allowlist validation.
- Disable OAuth provider registrations unless real client ID/secret values are present.

Regression tests/checks:

- Add tests for production cookie flags, redirect allowlist rejection, and disabled OAuth providers.

### SEC-008: Agent and worker unauthenticated routes depend on service-layer validation

Severity: Medium-high

Status: Resolved for the current provider-neutral callback/worker baseline; keep expanding negative tests before external execution is enabled.

Affected files:

- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`
- `src/main/java/com/strangequark/trasck/agent/AgentTaskController.java`
- `src/main/java/com/strangequark/trasck/agent/AgentWorkerController.java`
- `src/main/java/com/strangequark/trasck/agent/AgentCallbackJwtService.java`

Current state:

- `/api/v1/agent-callbacks/**` and `/api/v1/workspaces/*/agent-workers/**` are open at the HTTP security layer.
- Callback and worker authentication happens in agent services through signed callback JWTs and worker-token credentials.
- Repeated callback and generic worker authentication failures are rate-limited through the same durable security counter path used by login throttling.
- Callback and worker auth failures write durable security telemetry events.

Impact:

- This is an intentional architecture, but it is a high-value boundary. Any service-layer validation regression could expose task mutation or worker events.

Recommended fix:

- Keep these routes open only if service-layer validation remains exhaustive.
- Keep focused negative tests for missing, expired, wrong-provider, wrong-workspace, wrong-profile, wrong-task, and revoked token assertions.
- Add broader denial tests as new callback/worker actions are exposed.

Regression tests/checks:

- Expand agent callback and worker auth denial tests before external adapters are enabled.

### SEC-009: Attachment and import payload limits are not fully explicit

Severity: Medium

Status: Resolved for deployment defaults plus workspace-admin configurable byte/content-type limits; antivirus/content scanning remains a future provider boundary.

Affected files:

- `src/main/java/com/strangequark/trasck/workitem/WorkItemController.java`
- `src/main/java/com/strangequark/trasck/workitem/WorkItemCollaborationService.java`
- `src/main/java/com/strangequark/trasck/activity/storage/AttachmentStorageService.java`
- `src/main/java/com/strangequark/trasck/integration/ImportJobService.java`

Current state:

- Attachment storage validates checksum and storage path.
- Local path traversal is handled.
- Attachment metadata, file uploads, attachment downloads, generated export artifacts, export downloads, and import parser payloads enforce effective deployment/workspace/project size and content-type limits. Workspace admins can override deployment defaults through the workspace security policy API/UI, and project admins can apply inherited project overrides through the project security policy API/UI. Anonymous public attachment downloads are routed through short-lived app-signed backend URLs that are bound to project/work-item/attachment IDs and expiry.
- Multipart request sizes are environment-configurable.

Impact:

- Large or hostile uploads/imports are rejected before normal processing when they exceed configured limits or content-type policy.

Recommended fix:

- Add antivirus/content scanning hooks as a future provider boundary.

Regression tests/checks:

- Keep oversized upload/import/export rejection tests and content-type validation tests.

### SEC-010: Secret management is database-encrypted but not production-grade yet

Severity: Medium

Affected files:

- `src/main/java/com/strangequark/trasck/agent/SecretCipherService.java`
- `src/main/java/com/strangequark/trasck/agent/AgentService.java`
- `src/main/java/com/strangequark/trasck/automation/AutomationService.java`

Current state:

- AES-GCM encryption is implemented for provider credentials, callback private keys, and SMTP passwords.
- The encryption key is application config driven.
- There is no key ID, rotation workflow, envelope encryption, KMS/Vault integration, or per-secret audit metadata.

Impact:

- Database compromise alone does not reveal raw secrets, but app config compromise decrypts all DB secrets. Rotation is operationally limited.

Recommended fix:

- Keep DB encryption for local/dev.
- Add an external secret-management abstraction before production external agent execution or production SMTP credentials.
- Add key versioning and rotation migration strategy.

Regression tests/checks:

- Add redaction tests for all API responses, activity/audit entries, dispatch attempts, exports, and frontend previews.

### SEC-011: Frontend secret handling needs polish

Severity: Medium

Status: Resolved for the current admin/manual-test UI baseline.

Affected files:

- `../trasck-frontend/src/pages/TokenAdminPage.jsx`
- `../trasck-frontend/src/pages/automation/AutomationPage.jsx`
- `../trasck-frontend/src/pages/automation/AutomationDetailPages.jsx`

Current state:

- The frontend does not store access tokens in local storage.
- Created API tokens are displayed through a dedicated one-time reveal component with copy and clear actions.
- Generic JSON previews recursively redact secret-shaped fields.
- Webhook secret inputs use password fields.

Impact:

- Local shoulder-surfing or screen recording risk is reduced for current secret-bearing frontend paths.

Recommended fix:

- Continue replacing raw JSON escape hatches with field-specific secret-aware forms as admin screens mature.

Regression tests/checks:

- Add broader frontend tests proving access tokens are not persisted and secret fields/previews are masked.

### SEC-012: Frontend container and nginx security headers are minimal

Severity: Medium

Status: Resolved as a baseline container/static-server hardening pass.

Affected files:

- `../trasck-frontend/Dockerfile`
- `../trasck-frontend/nginx.conf.template`

Current state:

- Docker build uses `npm ci`.
- Nginx sends `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and a runtime-templated conservative CSP.

Impact:

- Builds are less reproducible, and browsers do not receive baseline protections such as `X-Content-Type-Options`, frame protection/CSP, or referrer policy.

Recommended fix:

- Keep deployed CSP values environment-driven and review them with production origins.

Regression tests/checks:

- Add a container/static config check for required headers.

### SEC-013: Dependency vulnerability automation is incomplete

Severity: Medium

Status: Resolved for baseline CI wiring; scanner cache/API-key strategy remains production CI work.

Affected files:

- `pom.xml`
- `../trasck-frontend/package.json`
- CI/Jenkins configuration

Current state:

- Frontend `npm audit --audit-level=high` currently reports zero vulnerabilities.
- Maven dependency tree resolves successfully.
- OWASP Dependency Check is wired through the backend `security-audit` Maven profile and required GitHub Actions workflow gate, with cache support and optional `NVD_API_KEY`.
- The Maven wrapper is executable so CI can invoke `./mvnw`.

Impact:

- Java CVEs are now checked by the CI profile, subject to scanner data availability and runtime.

Recommended fix:

- Keep the dependency-check gate mandatory, and configure an `NVD_API_KEY` secret if public scanner data throttling becomes noisy.
- Keep `npm audit` or equivalent in frontend CI.

Regression tests/checks:

- CI should fail on critical/high dependency vulnerabilities unless explicitly risk-accepted.

### SEC-014: Production error and logging policy needs review

Severity: Medium-low

Status: Resolved for centralized API error bodies and production server-error log messages; outbound-library-specific logs should continue to use the same redactor.

Affected files:

- Controllers and services that throw `ResponseStatusException`
- Domain event and audit projection services
- Import, automation, webhook, email, and agent worker paths

Current state:

- Many service errors remain clear and useful for local testing.
- Activity/audit payloads and centralized API errors/log messages redact token, password, secret, credential, authorization, and private-key shaped values.
- Production-like 5xx API responses use a generic message instead of exposing exception details.

Impact:

- Future code paths may accidentally return or log internal details, provider responses, URLs, email addresses, filenames, or secret-adjacent metadata.

Recommended fix:

- Keep outbound HTTP, OAuth provider, SMTP, and agent provider failures on the centralized redaction path when those call sites add dedicated logging.

Regression tests/checks:

- Add tests for representative error responses and redacted logs/events where practical.

### SEC-015: CI/deploy health checks are not aligned with backend health endpoint

Severity: Low

Affected file:

- `../trasck-frontend/Jenkinsfile`

Current state:

- The frontend Jenkinsfile checks `http://localhost:6100/`, while the backend health endpoint is `GET /api/trasck/health`.

Impact:

- This is not directly a data security flaw, but it can mask deployment health and delay detection of failed security config.

Recommended fix:

- Update health checks to `http://localhost:6100/api/trasck/health`.

Regression tests/checks:

- Add pipeline smoke checks for backend health and frontend asset serving.

## Production Hardening Priority

The next production-packaging hardening work should focus on:

1. Add production health probes and operations visibility for Redis-backed throttling now that production-like startup requires a successful Redis ping.
2. Design the browser UX/API shape for recent-login or step-up confirmation prompts before hosted system-admin operations.
3. Add broader redaction, authorization denial, tenant-isolation, outbound URL, attachment/export authorization, and frontend secret persistence regression coverage.
4. Plan the shared external secret-management abstraction for SMTP, S3-compatible storage, and non-stub agent credentials.
5. Keep the required OWASP Dependency Check gate stable with NVD API key/cache tuning if scanner throttling appears.

## Review Notes

- The operational and security changes made in this implementation pass were reviewed for obvious authorization, tenant-boundary, data-exposure, and local-runtime regressions.
- No hard-coded production credentials were found in source code. Real `.env` files have been removed from the repos and ignored; committed examples remain local-only.
- Development defaults, non-Redis rate-limit stores, and unreachable Redis-backed throttling are now rejected under production-like profiles.
- OpenAPI/Swagger production access now requires active system-admin status instead of workspace-admin role membership.
- System-admin list/grant/revoke APIs and frontend controls are implemented, with last-active-admin revocation protection and production-like recent-login step-up on grant/revoke.
- `system.admin` is not seeded into workspace/project role permissions, including wildcard workspace-owner roles.
- Outbound allowlisting now supports exact hosts, host:port entries, wildcard hosts, wildcard host:port entries, and CIDR ranges.
- Login/callback/worker auth failures now use durable database counters by default, Redis counters are mandatory and startup-validated in production-like profiles, and telemetry rows persist security-relevant failures.
- Attachment/import/export content limits are workspace-admin and project-admin configurable, and frontend secret-display hardening is implemented.
- Centralized API error/log redaction is implemented for production-facing API errors, while common malformed input, access-denial, missing route, unsupported method/media type, and upload-size failures keep explicit non-5xx statuses.
- No non-documentation `6101` config references were found.

## Verification Run

- Backend compile passed: `./mvnw -q -DskipTests compile`.
- Latest production-security follow-up compile passed: `mvn -q -DskipTests compile`.
- Targeted backend security/persistence rerun passed: `./mvnw -q -Dtest=LoginAttemptServiceTest,SecretTextRedactorTest,ProductionSecurityIntegrationTest,JpaPersistenceTest test`.
- Latest production-security follow-up tests passed: `mvn -q -Dtest=ProductionSecurityStartupValidatorTest,ProductionSecurityIntegrationTest test`.
- Full backend `./mvnw -q test` passed with 33 tests, 0 failures, 0 errors, and 0 skipped.
- Frontend `npm test -- --run` passed with 2 files and 12 tests.
- Latest frontend `npm test` passed with 2 files and 12 tests.
- Frontend `npm run build` passed; generated Vite `dist/` output is ignored.
- Frontend `npm audit --audit-level=high` reported zero vulnerabilities.
- Backend Java dependency-check is a required CI gate through `./mvnw -Psecurity-audit -DskipTests dependency-check:check`; CI uses dependency-check data caching and an optional `NVD_API_KEY` secret.
- Docker Compose config validation passed with the internal Redis service present and not host-published.
- Java Playwright harness test compilation passed with `mvn -q test-compile` in `../trasck-test`; Docker Compose config validation passed earlier.
- Only `.env.example` files remain in the two repos.
- `git diff --check` passed in the backend, frontend, and `../trasck-test` repos.
- No listeners were found on ports `6100` or `6101` after verification.
- No containers were exposing `6100`, `6101`, `5432`, or `55432` after verification when checked through `DOCKER_HOST=unix:///var/run/docker.sock`.
- No non-documentation `6101` runtime/config references were found.
