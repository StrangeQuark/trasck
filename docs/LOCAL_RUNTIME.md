# Trasck Backend Local Runtime

This backend is a Spring Boot 4 service on Java 21 with PostgreSQL and Flyway.

## Start With Docker Compose

From the backend project root:

```bash
docker compose up --build
```

The compose file starts:

- `trasck-db` on `localhost:5432`
- `maildev` SMTP on `localhost:1025` and the Maildev web UI on `localhost:1080`
- `trasck-service` on `localhost:6100`

The Vite frontend runs on `localhost:8080` during local development and reads the backend URL from `VITE_TRASCK_API_BASE_URL`, then `VITE_API_URL`, then `http://localhost:6100`.

Copy `.env.example` to `.env` for local development and keep `.env` untracked. Example values are development-only and must be replaced outside local testing.

## Start The Service Manually

Start PostgreSQL first:

```bash
docker compose up trasck-db
```

Then run the service:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/trasck \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=password \
TRASCK_JWT_SECRET=dev-only-change-me-dev-only-change-me-dev-only-change-me-32 \
TRASCK_SECRETS_ENCRYPTION_KEY=dev-only-change-me-dev-only-change-me-dev-only-change-me-32 \
TRASCK_OAUTH_ASSERTION_SECRET=dev-only-change-me-dev-only-change-me-dev-only-change-me-32 \
sh mvnw spring-boot:run
```

## Required Configuration

Core local variables:

- `SPRING_DATASOURCE_URL`: PostgreSQL JDBC URL.
- `SPRING_DATASOURCE_USERNAME`: PostgreSQL username.
- `SPRING_DATASOURCE_PASSWORD`: PostgreSQL password.
- `TRASCK_JWT_SECRET`: HMAC secret for user access JWTs.
- `TRASCK_SECRETS_ENCRYPTION_KEY`: secret material used by the encrypted DB secret abstraction.
- `TRASCK_OAUTH_ASSERTION_SECRET`: secret material used by the provider-neutral verified identity endpoint.
- `TRASCK_AUTH_COOKIE_SECURE`: defaults to `false` locally; production-like profiles require `true`.
- `TRASCK_OAUTH_SUCCESS_REDIRECT`: local OAuth success redirect defaults to `http://localhost:8080/auth/callback`; production-like profiles require a non-local URL.
- `CORS_ALLOWED_ORIGINS`: comma-separated browser origins allowed to use credentialed CORS requests.
- `TRASCK_OUTBOUND_URL_ALLOWED_HOSTS`: comma-separated exact host, host:port, wildcard host, wildcard host:port, or CIDR entries that may bypass the default-deny outbound URL policy for trusted local/private webhook, worker, OAuth side-fetch, or S3-compatible targets.
- `TRASCK_PASSWORD_MIN_LENGTH`, `TRASCK_LOGIN_MAX_FAILURES`, `TRASCK_LOGIN_FAILURE_WINDOW`, and `TRASCK_LOGIN_LOCKOUT_DURATION`: password and login throttling controls.
- `TRASCK_TRUST_FORWARDED_FOR`: defaults to `false`. Set only when Trasck is behind a trusted reverse proxy that controls `X-Forwarded-For`.
- `TRASCK_SECURITY_RATE_LIMIT_STORE`: `database` by default. Set to `redis` to share auth throttling counters across horizontally scaled backend instances.
- `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, and `SPRING_DATA_REDIS_PASSWORD`: Redis connection settings used when `TRASCK_SECURITY_RATE_LIMIT_STORE=redis`. Production-like profiles require the Redis-backed rate-limit store and fail startup if Redis cannot be pinged.
- `TRASCK_ATTACHMENTS_MAX_UPLOAD_BYTES`, `TRASCK_ATTACHMENTS_MAX_DOWNLOAD_BYTES`, and `TRASCK_ATTACHMENTS_ALLOWED_CONTENT_TYPES`: attachment metadata/upload/download limits and content-type allowlist.
- `TRASCK_EXPORTS_MAX_ARTIFACT_BYTES` and `TRASCK_EXPORTS_ALLOWED_CONTENT_TYPES`: generated export artifact and export download limits.
- `TRASCK_IMPORTS_MAX_PARSE_BYTES` and `TRASCK_IMPORTS_ALLOWED_CONTENT_TYPES`: import parser payload size and content-type allowlist.
- `TRASCK_MULTIPART_MAX_FILE_SIZE` and `TRASCK_MULTIPART_MAX_REQUEST_SIZE`: Spring multipart upload caps.
- `SPRING_JPA_OPEN_IN_VIEW`: defaults to `false`.
- `SPRING_JPA_HIBERNATE_DDL_AUTO`: defaults to `validate`; Flyway owns schema creation.
- `SPRING_MAIL_HOST`: defaults to `localhost`; Docker Compose sets it to `maildev`.
- `SPRING_MAIL_PORT`: defaults to `1025`.
- `TRASCK_EMAIL_PROVIDER`: defaults to `maildev` for the current development email provider.
- `TRASCK_EMAIL_FROM`: default sender address for automation email delivery rows.
- `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED`: defaults to `false`. Local/default profiles can use admin sample import endpoints when the workspace import setting is enabled. Production-like `prod`, `production`, `staging`, and `hosted` profiles require this variable to be `true` and the workspace `sampleJobsEnabled` setting to be enabled before sample jobs can be created.

Agent callback private keys and provider credentials are stored encrypted in `agent_provider_credentials.encrypted_secret`. `TRASCK_SECRETS_ENCRYPTION_KEY` can be raw text, hex, standard Base64, or URL-safe Base64. A direct 16, 24, or 32 byte decoded key is used as AES key material; otherwise Trasck derives an AES-256 key with SHA-256.

Production-like `prod`, `production`, `staging`, and `hosted` profiles fail startup when known development secrets, weak database passwords, insecure cookie flags, local OAuth redirects, local/wildcard CORS origins, non-Redis rate-limit stores, or unreachable Redis-backed throttling are active. OpenAPI and Swagger remain public in local/non-production profiles, but production-like profiles require authenticated system-admin access. Active system admins can be managed through `GET/POST/DELETE /api/v1/system-admins`; production-like grant/revoke requires recent authentication. Workspace admins can override deployment attachment/import/export limits and the workspace anonymous-read switch through `GET/PATCH /api/v1/workspaces/{workspaceId}/security-policy`, and project admins can apply project-level overrides plus project visibility through `GET/PATCH /api/v1/projects/{projectId}/security-policy`.

Docker Compose includes an internal `redis` service for `TRASCK_SECURITY_RATE_LIMIT_STORE=redis`. The Redis service is not published to the host by default.

## Security Verification

Backend dependency scanning is a required backend CI gate and can be run locally through:

```bash
./mvnw -Psecurity-audit -DskipTests dependency-check:check
```

Frontend dependency scanning should continue to use:

```bash
npm audit --audit-level=high
```

## Full-Stack Playwright Tests

The separate `../trasck-test` repository contains Java Playwright tests for backend API behavior and frontend browser workflows.

Start the backend and frontend first, then from `../trasck-test` run:

```bash
mvn test
```

The test repo defaults to:

- Backend: `http://localhost:6100`
- Frontend: `http://localhost:8080`
- Browser: `chromium`

Override these with `TRASCK_BACKEND_BASE_URL`, `TRASCK_FRONTEND_BASE_URL`, `TRASCK_E2E_BROWSER`, `TRASCK_E2E_HEADLESS`, and `TRASCK_E2E_TIMEOUT_MS`. Authenticated API coverage also supports `TRASCK_E2E_LOGIN_IDENTIFIER`, `TRASCK_E2E_LOGIN_PASSWORD`, `TRASCK_E2E_WORKSPACE_ID`, and `TRASCK_E2E_PROJECT_ID`; those tests create uniquely named resources and clean them up through public APIs where cleanup APIs exist. For a fresh disposable local database, `TRASCK_E2E_ALLOW_SETUP=true` lets the suite create the first admin/workspace/project through `/api/v1/setup` and verify that a second setup call is rejected. Setup bootstrap stays minimal; broader browser workflow data is opt-in through `TRASCK_E2E_SEED_SAMPLE_DATA=true` and a separate fixture. The test repo keeps a committed route coverage baseline at `src/test/resources/backend-route-coverage.tsv`, requires covered rows to identify their owning Java Playwright test in `coverageOwner`, fails when `/v3/api-docs` exposes a route missing from that baseline, writes a generated OpenAPI route inventory to `test-results/api/backend-route-inventory.tsv`, and currently tracks 230 covered backend routes, 0 ordinary planned routes, and 144 planned high-risk routes. It also supports `docker compose run --rm trasck-test` for running the Java Playwright suite from its container against services on the host.

## Health Check

```bash
curl http://localhost:6100/api/trasck/health
```

## Authentication Modes

Trasck accepts these API authentication shapes:

- Browser/cookie: `POST /api/v1/auth/login` sets the `trasck_access_token` HTTP-only cookie. Unsafe requests with that cookie need an `X-XSRF-TOKEN` header from `GET /api/v1/auth/csrf`.
- Direct Bearer JWT: use the `accessToken` returned from login as `Authorization: Bearer <jwt>`. CSRF is not required for Bearer-header calls.
- Personal/service API token: create a token through the auth token endpoints and use it as `Authorization: Bearer <api-token>`. Token scopes are enforced in addition to role permissions.
- Agent callback JWT: agent callbacks use `X-Trasck-Agent-Callback-Jwt`, scoped to provider/workspace/profile/task.
- Generic worker token: worker protocol calls use `X-Trasck-Worker-Token`, matched against an active encrypted `worker_token` credential on a `generic_worker` provider.

See `docs/http/trasck-api-examples.http` for runnable request examples.

## Development Email

Automation email actions create durable `email_deliveries` rows and can be processed manually or through workspace scheduled worker settings. In local development, Docker Compose provides Maildev. Keep worker email processing in dry-run mode unless you want the backend to send to the Maildev SMTP listener; Maildev messages are visible at `http://localhost:1080`.
