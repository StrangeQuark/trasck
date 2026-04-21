# Trasck Security Audit

Date: 2026-04-20

## Scope

This audit covers the current Trasck backend repo and Vite React frontend repo before broader manual product testing.

- Backend tracked files reviewed: 658
- Frontend tracked files reviewed: 84
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
- `npm audit --json` in the frontend returned zero reported vulnerabilities.
- `sh mvnw dependency:tree -DskipTests` completed successfully; no Java CVE scanner is currently wired into the repo.
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
- Production-like profiles fail startup when known development secrets, weak database passwords, insecure auth-cookie flags, local OAuth success redirects, or local/wildcard CORS origins are active.
- OpenAPI and Swagger stay public for local/non-production contract work, but production-like profiles require authenticated workspace-admin access.
- CORS is explicitly allowlist-based through `CORS_ALLOWED_ORIGINS`.
- Configured outbound HTTP targets are protected by a shared default-deny URL policy for localhost, private, link-local, carrier-grade NAT, unique-local IPv6, multicast, and metadata-like local destinations unless exact hosts are allowlisted.
- Human password creation now enforces a minimum length and rejects common development passwords; login failures are throttled by identifier plus remote address, and `X-Forwarded-For` is ignored unless explicitly trusted behind a controlled proxy.
- Saved-filter/query-builder SQL uses whitelisted fields/operators and bind parameters. Raw SQL-shaped keys are rejected in user-configurable report/filter/dashboard paths.
- Local filesystem attachment storage normalizes paths and rejects storage keys that escape the configured root.
- The frontend does not persist access tokens in local storage. Current local storage is limited to API base URL and selected UI/entity IDs.
- No `dangerouslySetInnerHTML`, `eval`, `new Function`, or direct `document.cookie` usage was found in current frontend source.
- No `6101` backend or frontend config references were found; current backend defaults remain on `6100`.
- Only `.env.example` files remain in the two repos; real `.env` files are ignored.
- Frontend container builds use `npm ci` and nginx now sends baseline security headers.

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

Status: Resolved for the current decision to keep production docs behind authenticated admin access.

Affected file:

- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`

Current state:

- `GET /v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` remain public only in non-production-like profiles.
- Production-like profiles require an authenticated principal with `workspace.admin` scope and at least one active workspace membership whose role grants `workspace.admin`.

Impact:

- Public production deployments would expose the full API surface, schemas, endpoint names, and internal workflow shape to unauthenticated users.

Recommended fix:

- Before hosted multi-tenant deployments, decide whether workspace-admin access is enough or whether docs need a dedicated system-admin/global permission.

Regression tests/checks:

- Add security tests proving docs are public in local/test only and denied or disabled under production-like profiles.

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

Status: Partially resolved; durable/distributed throttling and auth-failure audit events remain future work.

Affected files:

- `src/main/java/com/strangequark/trasck/identity/AuthService.java`
- `src/main/java/com/strangequark/trasck/setup/SetupRequestValidator.java`

Current state:

- Login failures are throttled by identifier plus remote address using configurable in-memory counters, failure window, and lockout duration.
- Spoofable `X-Forwarded-For` values are ignored by default and only used when `TRASCK_TRUST_FORWARDED_FOR=true` is set behind a trusted reverse proxy.
- New human passwords are validated for configured minimum length and common development password rejection during setup, invitation registration, and workspace-admin direct user creation.

Impact:

- Password guessing and weak account passwords are practical once exposed to the internet.

Recommended fix:

- Move throttling to database/Redis-backed counters before shared deployments if multiple app instances are expected.
- Add auth-failure audit events and consider stronger entropy checks once password reset/change flows exist.

Regression tests/checks:

- Add tests for weak password rejection, login throttling/lockout boundaries, and trusted-proxy forwarding behavior.

### SEC-006: Outbound HTTP targets need SSRF and egress controls

Severity: High before public hosting

Status: Resolved as a baseline default-deny policy with exact host/host:port allowlisting.

Affected files:

- `src/main/java/com/strangequark/trasck/automation/AutomationService.java`
- `src/main/java/com/strangequark/trasck/agent/AgentWorkerWebhookDispatchConsumer.java`
- `src/main/java/com/strangequark/trasck/activity/storage/S3CompatibleAttachmentStorageProvider.java`
- `src/main/java/com/strangequark/trasck/agent/AgentService.java`

Current state:

- A shared outbound URL policy validates webhooks, agent worker callback URLs, OAuth verified-email side-fetch URLs, and S3-compatible endpoints.
- The policy default-denies localhost, private networks, link-local ranges, carrier-grade NAT, unique-local IPv6, multicast, and metadata-like local destinations unless exact hosts are explicitly allowlisted.
- Outbound webhook, agent-worker, OAuth side-fetch, and S3-compatible calls now use bounded connect/request timeouts.

Impact:

- A workspace admin or compromised admin account could configure calls to internal services, cloud metadata endpoints, or private network resources.

Recommended fix:

- Decide whether self-hosted deployments need CIDR or wildcard allowlisting, or whether exact host/host:port entries stay sufficient for the first manual test.

Regression tests/checks:

- Add validation tests for denied localhost/private/link-local URLs and allowed external HTTPS URLs.

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

Affected files:

- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`
- `src/main/java/com/strangequark/trasck/agent/AgentTaskController.java`
- `src/main/java/com/strangequark/trasck/agent/AgentWorkerController.java`
- `src/main/java/com/strangequark/trasck/agent/AgentCallbackJwtService.java`

Current state:

- `/api/v1/agent-callbacks/**` and `/api/v1/workspaces/*/agent-workers/**` are open at the HTTP security layer.
- Callback and worker authentication happens in agent services through signed callback JWTs and worker-token credentials.

Impact:

- This is an intentional architecture, but it is a high-value boundary. Any service-layer validation regression could expose task mutation or worker events.

Recommended fix:

- Keep these routes open only if service-layer validation remains exhaustive.
- Add focused negative tests for missing, expired, wrong-provider, wrong-workspace, wrong-profile, wrong-task, and revoked token assertions.
- Add rate limiting and audit logging for repeated callback/worker auth failures.

Regression tests/checks:

- Expand agent callback and worker auth denial tests before external adapters are enabled.

### SEC-009: Attachment and import payload limits are not fully explicit

Severity: Medium

Affected files:

- `src/main/java/com/strangequark/trasck/workitem/WorkItemController.java`
- `src/main/java/com/strangequark/trasck/workitem/WorkItemCollaborationService.java`
- `src/main/java/com/strangequark/trasck/activity/storage/AttachmentStorageService.java`
- `src/main/java/com/strangequark/trasck/integration/ImportJobService.java`

Current state:

- Attachment storage validates checksum and storage path.
- Local path traversal is handled.
- Upload/download and import parsing paths still need explicit product-level size, content-type, and parser-safety limits.

Impact:

- Large or hostile uploads/imports can create memory pressure, slow parsing, or store unwanted content.

Recommended fix:

- Add maximum attachment size, allowed content-type policy, and import payload size limits.
- Add antivirus/content scanning hooks as a future provider boundary.
- Ensure generated export artifacts respect size limits and authorization.

Regression tests/checks:

- Add oversized upload/import rejection tests and content-type validation tests.

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

Affected files:

- `../trasck-frontend/src/pages/TokenAdminPage.jsx`
- `../trasck-frontend/src/pages/automation/AutomationPage.jsx`
- `../trasck-frontend/src/pages/automation/AutomationDetailPages.jsx`

Current state:

- The frontend does not store access tokens in local storage.
- Created API tokens are displayed once through JSON preview.
- Webhook secret inputs use normal text fields instead of masked secret inputs.

Impact:

- Local shoulder-surfing or screen recording can expose secrets more easily.

Recommended fix:

- Add dedicated one-time token reveal UI with clear warnings and quick clear action.
- Mask webhook secret fields.
- Avoid rendering secret-shaped response fields in generic JSON previews.

Regression tests/checks:

- Add frontend tests proving access tokens are not persisted and secret fields are masked.

### SEC-012: Frontend container and nginx security headers are minimal

Severity: Medium

Status: Resolved as a baseline container/static-server hardening pass.

Affected files:

- `../trasck-frontend/Dockerfile`
- `../trasck-frontend/nginx.conf`

Current state:

- Docker build uses `npm ci`.
- Nginx sends `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and a conservative CSP.

Impact:

- Builds are less reproducible, and browsers do not receive baseline protections such as `X-Content-Type-Options`, frame protection/CSP, or referrer policy.

Recommended fix:

- Parameterize production CSP/connect-src when deployment hostnames are finalized.

Regression tests/checks:

- Add a container/static config check for required headers.

### SEC-013: Dependency vulnerability automation is incomplete

Severity: Medium

Affected files:

- `pom.xml`
- `../trasck-frontend/package.json`
- CI/Jenkins configuration

Current state:

- Frontend `npm audit --json` currently reports zero vulnerabilities.
- Maven dependency tree resolves successfully.
- There is no checked-in Java CVE scanner or automated dependency security gate.

Impact:

- Java CVEs may not be caught during normal verification.

Recommended fix:

- Add OWASP Dependency Check, Snyk, Dependabot, GitHub dependency review, or another Java-compatible dependency scanner to CI.
- Keep `npm audit` or equivalent in frontend CI.

Regression tests/checks:

- CI should fail on critical/high dependency vulnerabilities unless explicitly risk-accepted.

### SEC-014: Production error and logging policy needs review

Severity: Medium-low

Affected files:

- Controllers and services that throw `ResponseStatusException`
- Domain event and audit projection services
- Import, automation, webhook, email, and agent worker paths

Current state:

- Many service errors are clear and useful for local testing.
- Broad error/log redaction exists for activity/audit payloads, but production error-body and log redaction policy is not centralized.

Impact:

- Future code paths may accidentally return or log internal details, provider responses, URLs, email addresses, filenames, or secret-adjacent metadata.

Recommended fix:

- Centralize API error response shaping for production.
- Add log redaction around outbound HTTP failures, OAuth provider failures, SMTP failures, and agent provider failures.

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

The next primary step should close the remaining findings in this order:

1. Add upload/import size and content controls.
2. Add frontend secret-display hardening and one-time token reveal cleanup.
3. Add callback/worker authentication failure rate limiting and audit events.
4. Add Java dependency vulnerability scanning and keep frontend audit in CI.
5. Add broader redaction, authorization denial, tenant-isolation, and outbound URL regression coverage.
6. Decide whether production docs access needs a system-admin/global permission.
7. Decide whether exact outbound allowlisting is enough or self-hosted deployments need CIDR/wildcard support.

## Review Notes

- The operational changes made in the same implementation pass were reviewed for obvious authorization and tenant-boundary regressions. Scheduled import review exports use existing queued export jobs and worker run/health rows; scheduled agent dispatch-attempt pruning uses workspace worker settings and existing export-before-prune retention behavior.
- No hard-coded production credentials were found in source code. Real `.env` files have been removed from the repos and ignored; committed examples remain local-only.
- Development defaults are now rejected under production-like profiles.
- No non-documentation `6101` config references were found.

## Verification Run

- Targeted backend security/auth run passed: `DOCKER_HOST=unix:///var/run/docker.sock sh mvnw -Dtest=LoginAttemptServiceTest,ProductionSecurityStartupValidatorTest,OutboundUrlPolicyTest,S3CompatibleAttachmentStorageProviderTest,AuthIntegrationTest,ProductionSecurityIntegrationTest,AgentIntegrationTest test` passed with 12 tests.
- Backend `DOCKER_HOST=unix:///var/run/docker.sock sh mvnw test` passed with 27 tests.
- Frontend `npm test -- --run` passed with 2 files and 10 tests.
- Frontend `npm run build` passed; generated `dist/` output was removed afterward.
- Frontend `npm audit --json` reported zero vulnerabilities.
- Only `.env.example` files remain in the two repos.
- `git diff --check` passed in both repos.
- No listeners were found on ports `6100`, `6101`, or `55432` after verification.
- No non-documentation `6101` runtime/config references were found.
