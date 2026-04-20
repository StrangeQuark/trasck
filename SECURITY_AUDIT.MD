# Trasck Security Audit

Date: 2026-04-20

## Scope

This audit covers the current Trasck backend repo and Vite React frontend repo before broader manual product testing.

- Backend tracked files reviewed: 658
- Frontend tracked files reviewed: 84
- Excluded from scope: `.git`, `target`, `node_modules`, `dist`, generated runtime data, and other build output directories.
- Included in scope: Java source, React source, generated OpenAPI/client artifacts, Flyway migrations, tests, Docker/nginx/build config, Jenkinsfiles, checked-in `.env` files, local runtime docs, HTTP examples, and import samples.

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
- Saved-filter/query-builder SQL uses whitelisted fields/operators and bind parameters. Raw SQL-shaped keys are rejected in user-configurable report/filter/dashboard paths.
- Local filesystem attachment storage normalizes paths and rejects storage keys that escape the configured root.
- The frontend does not persist access tokens in local storage. Current local storage is limited to API base URL and selected UI/entity IDs.
- No `dangerouslySetInnerHTML`, `eval`, `new Function`, or direct `document.cookie` usage was found in current frontend source.
- No `6101` backend or frontend config references were found; current backend defaults remain on `6100`.

## Findings

### SEC-001: Checked-in secret-bearing local environment files

Severity: High before public hosting

Affected files:

- `.env`
- `../trasck-frontend/.env`
- `.gitignore`
- `../trasck-frontend/.gitignore`

Current state:

- Both repos track `.env` files.
- Backend `.env` contains local database credentials and secret material examples.
- Frontend `.env` contains local API-base values, including a stale `VITE_API_URL=http://localhost:6110/` value that is currently masked by `VITE_TRASCK_API_BASE_URL=http://localhost:6100`.

Impact:

- The current values appear local-only, but tracking `.env` establishes an unsafe pattern and could lead to real production secrets being committed later.

Recommended fix:

- Stop tracking real `.env` files.
- Add `.env` to both `.gitignore` files.
- Commit `.env.example` files only, with clearly fake local values and a required-production-variable checklist.
- Normalize frontend example API URLs to `http://localhost:6100`.

Regression tests/checks:

- Add a CI secret scan that fails on real-looking private keys, JWT secrets, SMTP passwords, OAuth secrets, object-storage keys, provider credentials, worker tokens, and callback private keys.

### SEC-002: Production runtime can start with development secrets and passwords

Severity: High before public hosting

Affected files:

- `src/main/resources/application.properties`
- `docker-compose.yml`
- `.env`
- `docs/LOCAL_RUNTIME.md`

Current state:

- JWT secret, secret-encryption key, database password, maildev config, and cookie-secure settings have local defaults.
- These defaults are good for first-run development but unsafe if reused in `prod`, `production`, `staging`, or `hosted` profiles.

Impact:

- A misconfigured production deployment could share known signing/encryption secrets, use weak database credentials, or set auth cookies without the `Secure` flag.

Recommended fix:

- Add production-profile startup validation that refuses known dev values and missing required secrets.
- Require `TRASCK_JWT_SECRET`, `TRASCK_SECRETS_ENCRYPTION_KEY`, production database credentials, OAuth secrets when providers are enabled, SMTP secrets when SMTP is enabled, and `TRASCK_AUTH_COOKIE_SECURE=true`.
- Keep local defaults only in local docs/examples.

Regression tests/checks:

- Add profile-specific startup tests for allowed local defaults and rejected production defaults.

### SEC-003: Swagger/OpenAPI are public in every runtime profile

Severity: High before public hosting

Affected file:

- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`

Current state:

- `GET /v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` are `permitAll`.

Impact:

- Public production deployments would expose the full API surface, schemas, endpoint names, and internal workflow shape to unauthenticated users.

Recommended fix:

- Keep Swagger/OpenAPI public only in local/dev.
- For production, either disable Swagger/OpenAPI entirely or require admin auth.

Regression tests/checks:

- Add security tests proving docs are public in local/test only and denied or disabled under production-like profiles.

### SEC-004: CORS configuration property is defined but not enforced

Severity: Medium-high

Affected files:

- `src/main/resources/application.properties`
- `src/main/java/com/strangequark/trasck/config/SecurityConfig.java`

Current state:

- `cors.allowed-origins` exists, but there is no `CorsConfigurationSource` and the security chain does not enable `.cors(...)`.

Impact:

- Browser behavior depends on framework defaults instead of an explicit allowlist. This can break legitimate browser use or create accidental exposure if future config changes enable broad CORS.

Recommended fix:

- Add explicit CORS configuration sourced from `CORS_ALLOWED_ORIGINS`.
- Deny credentials from unlisted origins.
- Test localhost frontend origin and a denied origin.

Regression tests/checks:

- Add CORS integration tests for preflight and credentialed requests.

### SEC-005: Login and password policy are too weak for production

Severity: High before public hosting

Affected files:

- `src/main/java/com/strangequark/trasck/identity/AuthService.java`
- `src/main/java/com/strangequark/trasck/setup/SetupRequestValidator.java`

Current state:

- Login has no rate limit, lockout, throttling, or abuse tracking.
- Password validation currently relies on required text and does not enforce length or complexity.

Impact:

- Password guessing and weak account passwords are practical once exposed to the internet.

Recommended fix:

- Add IP/account based login throttling with audit events.
- Enforce a minimum password length and reject common or low-entropy passwords.
- Apply the same policy to setup, direct admin user creation, invitation registration, and password-bearing auth paths.

Regression tests/checks:

- Add tests for weak password rejection and login throttling/lockout boundaries.

### SEC-006: Outbound HTTP targets need SSRF and egress controls

Severity: High before public hosting

Affected files:

- `src/main/java/com/strangequark/trasck/automation/AutomationService.java`
- `src/main/java/com/strangequark/trasck/agent/AgentWorkerWebhookDispatchConsumer.java`
- `src/main/java/com/strangequark/trasck/activity/storage/S3CompatibleAttachmentStorageProvider.java`
- `src/main/java/com/strangequark/trasck/agent/AgentService.java`

Current state:

- Webhook URLs are validated for basic HTTP/S shape.
- Agent worker webhook `callbackUrl` and S3-compatible storage endpoints can point to arbitrary configured endpoints.
- There is no centralized private-network, metadata-service, localhost, or denylist/allowlist egress policy.
- Some outbound clients do not set explicit connect/read timeouts.

Impact:

- A workspace admin or compromised admin account could configure calls to internal services, cloud metadata endpoints, or private network resources.

Recommended fix:

- Add a shared outbound URL policy for webhooks, agent worker callbacks, OAuth side fetches where relevant, and S3-compatible endpoints.
- Default-deny private IP ranges, localhost, link-local, and metadata-service addresses unless an explicit admin/deployment allowlist enables them.
- Add bounded timeouts to every outbound HTTP client.

Regression tests/checks:

- Add validation tests for denied localhost/private/link-local URLs and allowed external HTTPS URLs.

### SEC-007: Auth cookies and OAuth redirects need production guardrails

Severity: Medium-high

Affected files:

- `src/main/java/com/strangequark/trasck/identity/AuthController.java`
- `src/main/java/com/strangequark/trasck/identity/OAuth2LoginSuccessHandler.java`
- `src/main/java/com/strangequark/trasck/config/OAuthClientConfig.java`

Current state:

- Auth cookies are `HttpOnly`, `SameSite=Lax`, and optionally `Secure`, but `Secure` defaults to false.
- OAuth success redirect defaults to localhost.
- OAuth client registrations use disabled placeholder credentials when not configured.

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

Affected files:

- `../trasck-frontend/Dockerfile`
- `../trasck-frontend/nginx.conf`

Current state:

- Docker build uses `npm install` instead of `npm ci`.
- Nginx serves the SPA without security headers.

Impact:

- Builds are less reproducible, and browsers do not receive baseline protections such as `X-Content-Type-Options`, frame protection/CSP, or referrer policy.

Recommended fix:

- Use `npm ci` in container builds.
- Add production headers: `X-Content-Type-Options: nosniff`, `Referrer-Policy`, frame restrictions, and a conservative CSP that matches the chosen deployment/API origin.

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

The next primary step should close findings in this order:

1. Remove tracked `.env` files from normal source control, add `.env.example`, and add production startup validation for required secrets and cookie settings.
2. Gate Swagger/OpenAPI and implement explicit CORS.
3. Add outbound URL/egress policy and HTTP timeouts.
4. Add login/password protections.
5. Add upload/import size and content controls.
6. Add frontend/container security header and secret-display hardening.
7. Add dependency scanning and security regression tests.

## Review Notes

- The operational changes made in the same implementation pass were reviewed for obvious authorization and tenant-boundary regressions. Scheduled import review exports use existing queued export jobs and worker run/health rows; scheduled agent dispatch-attempt pruning uses workspace worker settings and existing export-before-prune retention behavior.
- No hard-coded production credentials were found in source code. The major config risk is tracked local `.env` files and development defaults that must be rejected under production-like profiles.
- No `6101` config references were found. The frontend checked-in `.env` should still be normalized because it contains an unused `6110` fallback value.

## Verification Run

- Backend `DOCKER_HOST=unix:///var/run/docker.sock sh mvnw test` passed with 18 tests.
- Frontend `npm test` passed with 2 files and 10 tests.
- Frontend `npm run build` passed; generated `dist/` output was removed afterward.
- Frontend `npm audit --json` reported zero vulnerabilities.
- OpenAPI/client artifacts were regenerated from a temporary backend on port `6100`; the temporary backend and temporary Postgres container were stopped afterward.
