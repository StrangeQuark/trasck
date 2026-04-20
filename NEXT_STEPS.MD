# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should queued import review CSV export jobs be picked up by the shared scheduled worker next, or remain manually processed through the admin endpoint until a broader export-job worker design exists?
    - Answer: Allow it to be picked up by the shared scheduled worker
2. Should agent dispatch attempt retention get workspace-level defaults and automatic scheduled pruning, or remain manual admin export/prune until real external adapters are enabled?
    - Answer: Give workspace-level default and automatic scheduled pruning
3. Should the first real Codex/Claude execution pass start with provider-specific credential validation, or start with a generic external-worker protocol that Codex/Claude adapters can implement later?
    - Answer: Start with real provider-specific credential validation.

## Current State

The backend has the full initial relational schema, Flyway migrations, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered, versioned, diffed, materialized, conflicted, resolved, rerun, completed with guarded open-conflict acceptance, and exercised through checked-in CSV/Jira/Rally sample payload flows. Reusable transform presets have immutable version records, clone-from-version recovery, clone-and-retarget preview/apply, template-local overrides, value lookups, type translations, status translations, materialization snapshots, exact/modified reruns, and conflict-resolution worker pickup.

Import review operations now have backend-generated export-job artifacts for job-level version diffs, conflict-resolution job audit tables, import export-artifact tables, and project/workspace import-completion metrics. The frontend passes current table filter state into queued backend CSV export jobs, lets admins process queued review exports, and records completed artifacts in the shared export-job history/download flow. Smaller tables that do not yet need durable audit artifacts can still use local client-side CSV export.

Import demo job endpoints remain workspace-admin-only. Local/default runtimes default sample jobs on for developer walkthroughs but workspace admins can toggle `sampleJobsEnabled` per workspace through import settings; production-like `prod`, `production`, `staging`, and `hosted` profiles require both the workspace setting and explicit deployment enablement with `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED=true`.

Automation, webhook, email, and import conflict-resolution worker executions write dedicated `automation_worker_runs` and `automation_worker_health` rows. Operations screens can filter run/health previews by worker type. Manual worker-run retention export and pruning accept `workerType`, `triggerType`, `status`, and start-time range filters, so admins can export/prune targeted worker history without deleting unrelated run records. Automatic retention pruning remains all-worker-type by design.

Agent delivery hardening keeps the generic worker protocol as the main extensibility path while Codex and Claude Code adapters expose explicit `stub`, `hosted_api`, and hardened `cli_worker` runtime modes behind the provider-neutral adapter contract. External execution must be intentionally enabled in provider config; CLI-worker config accepts named allowlisted command profiles instead of raw shell commands. Provider runtime preview lets admins inspect non-stub payloads without dispatching work. Dispatch, retry, and cancel boundaries are persisted in first-class `agent_dispatch_attempts` rows, included in `AgentTaskResponse.dispatchAttempts`, and exposed through workspace admin list/export/prune endpoints so admins and tests have a durable view of idempotency keys, payloads, external task IDs, transport, provider type, and failure messages before real external execution is enabled.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent flows, worker retention export/prune/automatic schedule controls, worker-type filtered operations previews, queued backend-generated filtered import review CSV export artifacts with admin processing controls, import diff export artifact history/download controls, import completion metrics tables, agent runtime preview and dispatch-attempt admin controls, and Vitest/Testing Library coverage.

## Primary Next Steps

### 1. Full Codebase Security Audit And Threat Model

Goal: inspect the entire Trasck backend and frontend before manual product testing, with a production security mindset.

Planned work:

- Review every backend file, every frontend file, generated client artifacts, Docker/nginx/build config, local runtime docs, checked-in HTTP examples, migrations, tests, and sample data for secrets, unsafe defaults, information disclosure, insecure auth flows, missing authorization, injection risks, path traversal, unsafe file handling, SSRF/open redirect risk, logging leaks, dependency risk, and data exposure.
- Build a system-level threat model after the file-by-file pass, covering authentication, OAuth, API tokens, service tokens, cookie/CSRF behavior, CORS, public project reads, workspace/project/team scoping, attachments, imports, exports, audit/activity logs, notifications, automation, webhooks, email, reporting, dashboards, generated OpenAPI/client behavior, and AI agent dispatch/callback/worker flows.
- Verify all secret-bearing configuration is environment-driven for deployable runtimes. No production credential, token, key, password, SMTP secret, OAuth client secret, database credential, object-storage secret, agent credential, callback private key, webhook secret, or JWT signing secret should be hard-coded or required from checked-in config.
- Produce a written security findings list with severity, affected files/endpoints, exploit path, user-data impact, recommended fix, and regression-test plan.

Done when:

- We have a complete security audit record for both repos, including file-level findings and cross-system risks, and no high-risk area has been skipped or assumed safe without inspection.

### 2. Production Security Hardening And Regression Coverage

Goal: close the security findings before manual end-to-end testing so manual testing starts from a hardened baseline.

Planned work:

- Move unsafe deployable defaults and secret-bearing configuration behind `.env`/environment variables, document required variables, and ensure local examples remain clearly non-production.
- Tighten backend security where findings require it: cookie flags, CSRF boundaries, CORS, OAuth callback validation, JWT/key handling, token scope enforcement, permission checks, tenant scoping, public-read gates, request validation, upload/download safety, export/download authorization, redaction, audit logging, rate/size limits where practical, and secure error responses.
- Tighten integration and worker surfaces: webhook validation, email/SMTP secret handling, import payload limits and parser safety, export artifact access, automation execution boundaries, AI agent runtime/callback/worker credential handling, and prevention of secrets leaking into task context, dispatch attempts, activity, audit, comments, exports, or frontend JSON previews.
- Tighten frontend security where findings require it: no browser access-token persistence, CSRF handling, API-base configuration safety, route/data exposure checks, dangerous rendering review, generated-client usage, admin-only screen assumptions, and accidental display of secret-bearing payloads.
- Add targeted backend/frontend tests for every fixed class of issue, including authorization denial, cross-workspace/project isolation, redaction, secret config, attachment/export access, callback/token validation, and frontend no-secret/no-token persistence checks.
- Run dependency and build verification for both repos, including Maven tests, frontend tests, frontend build, dependency audit where available, and any static checks added during the hardening pass.

Done when:

- All critical/high security findings are fixed, medium findings are fixed or explicitly documented with a production-safe mitigation plan, no real-user data leak paths remain known, and both repos pass the security regression suite.

### 3. Manual End-To-End Product Test Pass

Goal: validate the now-operable automation, webhook, email, import, board, dashboard, and agent surfaces against realistic local data before public testing.

Planned work:

- Exercise configured webhook, email, automation, import conflict-resolution, worker retention, generic-worker delivery, board moves, dashboards, and imports through the APIs/UI without database access.
- Add focused permission, activity, audit, retry/cancel, dead-letter, retention, and frontend route tests where the manual pass finds fragile behavior.
- Keep SMTP passwords database-encrypted for now, while preserving the secret-cipher abstraction so SMTP and agent credentials can move behind Vault/KMS/Secrets Manager later.

Done when:

- A local tester can operate delivery workers, inspect failures, retry/cancel jobs, export/prune worker history, create guarded sample imports, review/import conflict outcomes, and inspect agent task dispatch attempts without touching the database.

### 4. Queued Operations Worker Consolidation

Goal: reduce manual-only admin processing paths before broader user testing.

Planned work:

- Decide whether queued import review CSV exports should be picked up by the shared scheduled worker or wait for a generic export-job worker.
- Align import review export jobs, audit/retention export jobs, and agent dispatch-attempt export jobs around one operations model where practical.
- Keep external agent execution disabled by default while preserving runtime preview and dispatch-attempt inspection.

Done when:

- Admins can understand which queued/export/retention jobs are manual, which are scheduled, and where each job's artifact or failure can be inspected.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Complete the full codebase security audit and production hardening pass before manual product testing begins.
- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, personalization/custom-field paths, automation workers, notification defaults, import parsing/materialization/record editing/history/conflict resolution, and worker history as more APIs are exposed.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, notification defaults, worker runs, import review export jobs, import record versions, clone-retarget, snapshot reruns, and external callback/delivery failures as those flows are added.
- Review dedicated automatic worker-run pruning interval/window defaults after manual worker history grows enough to validate cadence.
- Keep SMTP secrets database-encrypted until the shared external secret-management abstraction is introduced for both SMTP and non-stub agent credentials, and ensure all secret-bearing deployment configuration comes from environment variables rather than checked-in files.
- Manually test a larger real-world import set through the UI, including field diff tables, backend-generated review CSV export artifacts, export artifact downloads, scheduled conflict pickup, and guarded sample job creation, before expanding import update/delete semantics further.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Keep multi-predicate custom-field filtering behind saved-filter/query-builder execution unless product usage proves direct project list query params need it too.
- Continue expanding targeted screen required-field checks only for command endpoints that can invalidate a required field.

### Test Readiness

- Add a dedicated security regression suite for tenant isolation, permission denial, public-read boundaries, token/callback validation, CSRF/cookie behavior, CORS expectations, redaction, attachment/export access, and frontend token/secret persistence.
- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, notification defaults, automation workers, worker history/health, import materialization/conflicts/record editing/history, agent dispatch attempts, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Expand automation tests for queued delivery retries, dead-letter handling, scheduled workers, worker settings, manual and automatic worker run retention schedule/window/throttle behavior, worker-type/trigger/status/date-range retention filters, SMTP provider settings, and Maildev/SMTP email delivery.
- Expand import tests from parser/materialization/sample-fixture/backend-sample coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, conflict update semantics, production-profile sample gating, guarded completion reporting screens, durable review CSV export jobs, and field-specific import review UI assertions.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, dispatch attempts, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Keep normal browser auth on cookie plus CSRF with no browser access-token local storage.
- Use the route-based frontend shell and global toast/error handling as the manual-test surface.
- Use the checked-in generated TypeScript API client and regenerate it only when the backend contract changes.
- Keep feature-specific frontend API services as the boundary between UI components and the generated client.
- Replace remaining raw JSON child editors with field-specific forms for common nested admin/import records.
- Expand frontend route tests and replace remaining raw ID inputs only after corresponding list endpoints exist.
- Keep board drag/drop card-level insertion covered in route/component tests as the board UI grows.
- Exercise import sample-data walkthroughs, import workspace sample settings, field-specific import diff tables, column filters, backend-generated review CSV export jobs, export artifact downloads, and import completion dashboard screens during manual end-to-end testing.
