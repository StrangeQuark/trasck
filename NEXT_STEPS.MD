# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should production-like profile validation fail startup when known development defaults are active, including JWT/encryption secrets, database passwords, insecure cookie flags, and missing required production secrets, or should the first hardening pass warn only until deployment packaging is finalized?
    - Answer: Yes, fail startup for production like profiles
2. Should Swagger/OpenAPI be disabled entirely outside local/dev, or stay available in production behind authenticated admin access?
    - Answer: Stay available in prod behind authenticated admin access
3. Should the outbound URL policy default-deny localhost, private-network, link-local, and cloud metadata endpoints for webhooks, agent worker callbacks, OAuth side fetches, and S3-compatible storage unless explicitly allowlisted?
    - Answer: Yes, default-deny those destinations
4. Should checked-in `.env` files be removed from source control now and replaced with committed `.env.example` files in both repos, or wait until a deployment secrets strategy is written?
    - Answer: Yes remove them now and replace with .env.example only

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

Queued import review CSV export jobs can now be picked up by the shared scheduled worker through workspace worker settings. Scheduled and manual review export processing writes `import_review_export` worker run and health rows, and operations screens can filter that worker type alongside automation, webhook, email, and import conflict-resolution workers. Agent dispatch attempt retention now has workspace-level defaults and optional automatic scheduled pruning. Codex and Claude Code provider credential creation now validates provider-specific credential types and basic secret/auth-scheme shape before encrypting credentials.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent flows, worker retention export/prune/automatic schedule controls, worker-type filtered operations previews, queued backend-generated filtered import review CSV export artifacts with admin processing controls, import diff export artifact history/download controls, import completion metrics tables, agent runtime preview and dispatch-attempt admin controls, and Vitest/Testing Library coverage.

The full production-minded backend/frontend security audit is recorded in `SECURITY_AUDIT.MD`. The audit found no `6101` configuration references and no frontend access-token local storage, but it identified production hardening work that must be completed before broad manual testing: remove tracked `.env` files in favor of examples, reject unsafe production defaults, gate Swagger/OpenAPI, add explicit CORS, add outbound egress/SSRF controls and timeouts, tighten login/password policy, enforce upload/import size controls, harden frontend secret display and container headers, and add security/dependency regression gates.


## Primary Next Steps

### 1. Production Security Hardening And Regression Coverage

Goal: close the security findings before manual end-to-end testing so manual testing starts from a hardened baseline.

Planned work:

- Work through `SECURITY_AUDIT.MD` findings in priority order.
- Move unsafe deployable defaults and secret-bearing configuration behind environment variables, document required variables, untrack real `.env` files, and keep committed examples clearly non-production.
- Tighten backend security where findings require it: production startup validation, cookie flags, CSRF boundaries, explicit CORS, Swagger/OpenAPI gating, OAuth redirect/provider validation, JWT/key handling, token scope enforcement, permission checks, tenant scoping, public-read gates, request validation, upload/download safety, export/download authorization, redaction, audit logging, rate/size limits where practical, and secure error responses.
- Tighten integration and worker surfaces: outbound URL/SSRF policy, HTTP timeouts, webhook validation, email/SMTP secret handling, import payload limits and parser safety, export artifact access, automation execution boundaries, AI agent runtime/callback/worker credential handling, and prevention of secrets leaking into task context, dispatch attempts, activity, audit, comments, exports, or frontend JSON previews.
- Tighten frontend security where findings require it: no browser access-token persistence, CSRF handling, API-base configuration safety, route/data exposure checks, dangerous rendering review, generated-client usage, admin-only screen assumptions, masked secret fields, one-time token reveal handling, container security headers, reproducible Docker builds, and accidental display of secret-bearing payloads.
- Add targeted backend/frontend tests for every fixed class of issue, including authorization denial, cross-workspace/project isolation, production secret config, CORS, Swagger/OpenAPI denial, egress URL validation, login/password protections, redaction, attachment/export access, callback/token validation, and frontend no-secret/no-token persistence checks.
- Run dependency and build verification for both repos, including Maven tests, frontend tests, frontend build, `npm audit`, Java dependency vulnerability scanning once added, and any static checks added during the hardening pass.

Done when:

- All critical/high security findings are fixed, medium findings are fixed or explicitly documented with a production-safe mitigation plan, no real-user data leak paths remain known, and both repos pass the security regression suite.

### 2. Manual End-To-End Product Test Pass

Goal: validate the now-operable automation, webhook, email, import, board, dashboard, and agent surfaces against realistic local data before public testing.

Planned work:

- Exercise configured webhook, email, automation, import conflict-resolution, worker retention, generic-worker delivery, board moves, dashboards, and imports through the APIs/UI without database access.
- Add focused permission, activity, audit, retry/cancel, dead-letter, retention, and frontend route tests where the manual pass finds fragile behavior.
- Keep SMTP passwords database-encrypted for now, while preserving the secret-cipher abstraction so SMTP and agent credentials can move behind Vault/KMS/Secrets Manager later.

Done when:

- A local tester can operate delivery workers, inspect failures, retry/cancel jobs, export/prune worker history, create guarded sample imports, review/import conflict outcomes, and inspect agent task dispatch attempts without touching the database.

### 3. External Worker And Provider Readiness

Goal: prepare the provider-neutral worker boundary for the first non-stub external agent execution without weakening normal Jira/Rally-style product behavior.

Planned work:

- Keep external agent execution disabled by default until security hardening and manual operations testing are complete.
- Validate Codex and Claude Code provider credential UX and errors against the provider-specific credential rules.
- Exercise generic worker claim/heartbeat/log/message/artifact/cancel/retry and webhook-push dispatch against a local worker harness.
- Decide whether the first real execution path should be hosted provider API, backend-managed CLI worker, or a deployable generic worker service once the hardened boundary is proven.

Done when:

- Admins can preview runtime payloads, rotate/revoke credentials, inspect dispatch attempts, and test a local worker without enabling paid or unmanaged external execution.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Complete the production hardening pass from `SECURITY_AUDIT.MD` before broad manual product testing begins.
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

- Add a dedicated security regression suite mapped to `SECURITY_AUDIT.MD` for tenant isolation, permission denial, public-read boundaries, token/callback validation, production secret/config validation, Swagger/OpenAPI gating, CSRF/cookie behavior, CORS expectations, outbound URL denial, redaction, attachment/export access, and frontend token/secret persistence.
- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, notification defaults, automation workers, worker history/health, import materialization/conflicts/record editing/history, agent dispatch attempts, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Expand automation tests for queued delivery retries, dead-letter handling, scheduled workers, worker settings, scheduled import review export pickup, manual and automatic worker run retention schedule/window/throttle behavior, worker-type/trigger/status/date-range retention filters, SMTP provider settings, and Maildev/SMTP email delivery.
- Expand import tests from parser/materialization/sample-fixture/backend-sample coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, conflict update semantics, production-profile sample gating, guarded completion reporting screens, durable review CSV export jobs, and field-specific import review UI assertions.
- Expand agent adapter contract tests covering provider-specific credential validation, automatic dispatch-attempt retention pruning, callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
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
