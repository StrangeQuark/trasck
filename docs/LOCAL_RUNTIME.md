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

Default local credentials are defined in `.env`. They are development-only values and must be replaced outside local testing.

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
sh mvnw spring-boot:run
```

## Required Configuration

Core local variables:

- `SPRING_DATASOURCE_URL`: PostgreSQL JDBC URL.
- `SPRING_DATASOURCE_USERNAME`: PostgreSQL username.
- `SPRING_DATASOURCE_PASSWORD`: PostgreSQL password.
- `TRASCK_JWT_SECRET`: HMAC secret for user access JWTs.
- `TRASCK_SECRETS_ENCRYPTION_KEY`: secret material used by the encrypted DB secret abstraction.
- `SPRING_JPA_OPEN_IN_VIEW`: defaults to `false`.
- `SPRING_JPA_HIBERNATE_DDL_AUTO`: defaults to `validate`; Flyway owns schema creation.
- `SPRING_MAIL_HOST`: defaults to `localhost`; Docker Compose sets it to `maildev`.
- `SPRING_MAIL_PORT`: defaults to `1025`.
- `TRASCK_EMAIL_PROVIDER`: defaults to `maildev` for the current development email provider.
- `TRASCK_EMAIL_FROM`: default sender address for automation email delivery rows.
- `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED`: defaults to `false`. Local/default profiles can use admin sample import endpoints when the workspace import setting is enabled. Production-like `prod`, `production`, `staging`, and `hosted` profiles require this variable to be `true` and the workspace `sampleJobsEnabled` setting to be enabled before sample jobs can be created.

Agent callback private keys and provider credentials are stored encrypted in `agent_provider_credentials.encrypted_secret`. `TRASCK_SECRETS_ENCRYPTION_KEY` can be raw text, hex, standard Base64, or URL-safe Base64. A direct 16, 24, or 32 byte decoded key is used as AES key material; otherwise Trasck derives an AES-256 key with SHA-256.

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
