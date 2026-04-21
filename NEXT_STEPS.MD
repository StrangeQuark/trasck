# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should notification read coverage add a small backend/admin fixture endpoint for creating deterministic test notifications, or wait until automation/notification delivery tests create real notifications through existing public APIs?
    - Answer: Wait until real notifications
2. Should program dashboard summary coverage wait for full program CRUD/project-assignment APIs, or should the backend expose a minimal program fixture/admin API now so Playwright can create program data through public routes?
    - Answer: Wait for the full APIs, but keep a note in NEXT_STEPS.MD that we need to take this work in the future
3. Should workspace invitation and workspace-admin-created user coverage get public cleanup endpoints next, such as invitation cancel and user deactivate/remove, before repeated Playwright runs target reused local stacks?
    - Answer: Yes implement those endpoints now
4. Should public project read Playwright coverage wait only for project visibility settings APIs, or should the next project settings pass also expose the anonymous-read toggles needed to build the full public-read test setup?
    - Answer: Build the full public-read test setup

## Current State

The backend has the full initial relational schema, Flyway migrations, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list/delete, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, system administration, workspace/project security policy, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, system-admin and workspace security-policy flows, a Project Settings route that currently hosts project security-policy controls and is the planned home for future project-admin settings, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered, versioned, diffed, materialized, conflicted, resolved, rerun, completed with guarded open-conflict acceptance, and exercised through checked-in CSV/Jira/Rally sample payload flows. Reusable transform presets have immutable version records, clone-from-version recovery, clone-and-retarget preview/apply, template-local overrides, value lookups, type translations, status translations, materialization snapshots, exact/modified reruns, and conflict-resolution worker pickup.

Import review operations now have backend-generated export-job artifacts for job-level version diffs, conflict-resolution job audit tables, import export-artifact tables, and project/workspace import-completion metrics. The frontend passes current table filter state into queued backend CSV export jobs, lets admins process queued review exports, and records completed artifacts in the shared export-job history/download flow. Smaller tables that do not yet need durable audit artifacts can still use local client-side CSV export.

Import demo job endpoints remain workspace-admin-only. Local/default runtimes default sample jobs on for developer walkthroughs but workspace admins can toggle `sampleJobsEnabled` per workspace through import settings; production-like `prod`, `production`, `staging`, and `hosted` profiles require both the workspace setting and explicit deployment enablement with `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED=true`.

Automation, webhook, email, and import conflict-resolution worker executions write dedicated `automation_worker_runs` and `automation_worker_health` rows. Operations screens can filter run/health previews by worker type. Manual worker-run retention export and pruning accept `workerType`, `triggerType`, `status`, and start-time range filters, so admins can export/prune targeted worker history without deleting unrelated run records. Automatic retention pruning remains all-worker-type by design.

Agent delivery hardening keeps the generic worker protocol as the main extensibility path while Codex and Claude Code adapters expose explicit `stub`, `hosted_api`, and hardened `cli_worker` runtime modes behind the provider-neutral adapter contract. External execution must be intentionally enabled in provider config; CLI-worker config accepts named allowlisted command profiles instead of raw shell commands. Provider runtime preview lets admins inspect non-stub payloads without dispatching work. Dispatch, retry, and cancel boundaries are persisted in first-class `agent_dispatch_attempts` rows, included in `AgentTaskResponse.dispatchAttempts`, and exposed through workspace admin list/export/prune endpoints so admins and tests have a durable view of idempotency keys, payloads, external task IDs, transport, provider type, and failure messages before real external execution is enabled. Repository connections can now be deactivated through the public workspace admin API so test and admin flows can clean up connection records without direct database access.

Queued import review CSV export jobs can now be picked up by the shared scheduled worker through workspace worker settings. Scheduled and manual review export processing writes `import_review_export` worker run and health rows, and operations screens can filter that worker type alongside automation, webhook, email, and import conflict-resolution workers. Agent dispatch attempt retention now has workspace-level defaults and optional automatic scheduled pruning. Codex and Claude Code provider credential creation now validates provider-specific credential types and basic secret/auth-scheme shape before encrypting credentials.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client that is refreshed after backend endpoint batches, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token/system-admin/workspace-security-policy/project-security-policy screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent flows, worker retention export/prune/automatic schedule controls, worker-type filtered operations previews, queued backend-generated filtered import review CSV export artifacts with admin processing controls, import diff export artifact history/download controls, import completion metrics tables, agent runtime preview and dispatch-attempt admin controls, and Vitest/Testing Library coverage.

The separate `trasck-test` repository now holds the Java Playwright automation harness for cross-repo testing. It has Maven-based Playwright configuration, environment-backed backend/frontend base URLs, optional authenticated smoke credentials plus workspace/project IDs, optional disposable first-run setup bootstrap through `/api/v1/setup`, separate opt-in sample-data fixture support that cleans up after each test by default, Docker support, reusable API/browser fixtures, unique-data helpers, redacted API snippets, committed OpenAPI route coverage baseline enforcement with `coverageOwner` on covered rows, generated OpenAPI route inventory output, screenshots, traces, deterministic local-stack guidance, API smoke tests for backend health, CSRF metadata, setup endpoint reachability, setup first-run-only conflict behavior, unauthenticated current-user denial, optional login/current-user cookie and Bearer sessions, local OpenAPI availability, authenticated API coverage for workspace/project security policy, personal and service API token scope/redaction/revocation behavior, system-admin/audit/export reads, configuration/custom-field/screen/report-query/reporting-retention CRUD, work item collaboration/activity and command routes, attachment byte upload/download, workspace and work-item label cleanup, team/membership/planning/board/swimlane/release/roadmap resources, reporting history/snapshot reads and snapshot execution controls, team-scoped dashboard/filter/view/report-query lists, repository connections, invitations, workspace-admin user creation, notification preference/default CRUD, dashboards/widgets, saved filters, saved views, favorites, recent items, resource cleanup through public APIs where public cleanup APIs exist, and a browser smoke test for the frontend shell. The committed baseline currently tracks 212 covered backend routes, 3 planned lower-risk routes, and 144 planned high-risk routes. The remaining lower-risk gaps are fixture-gated; the next broad testing work should move into high-risk asynchronous worker/import/export/agent surfaces once those fixture questions are settled.

The full production-minded backend/frontend security audit is recorded in `SECURITY_AUDIT.MD`. The current hardening baseline removes checked-in `.env` files in favor of `.env.example`, rejects unsafe production-like startup defaults, requires Redis-backed auth throttling in production-like profiles and pings Redis on startup, gates Swagger/OpenAPI behind dedicated system-admin access in production-like profiles, exposes system-admin REST/UI management with last-active-admin protection and production-like recent-login step-up for grant/revoke, enables explicit CORS, adds default-deny outbound URL/SSRF policy with exact host, wildcard host, and CIDR allowlisting, tightens password/login protections with default-untrusted `X-Forwarded-For`, rate-limits callback/worker auth failures, enforces workspace-admin and project-admin configurable attachment/import/export size and content-type limits, masks frontend secret display, centralizes redacted production API error/log handling while preserving explicit client-error statuses, templates production nginx CSP, makes Java dependency-check a required CI gate with cache/API-key support, keeps frontend `npm audit` in verification, updates frontend container builds to `npm ci`, and adds baseline nginx security headers. The audit still tracks production follow-ups: stronger secret-management/KMS abstraction, system-admin step-up UX hardening, production deployment health/observability around Redis-backed throttling, and broader dedicated security regression coverage.


## Primary Next Steps

### 1. Resolve Fixture-Gated Lower-Risk Route-Baseline Playwright Gaps

Goal: close or explicitly defer the last three non-worker, non-external backend API gaps from the committed route coverage baseline.

Planned work:

- Add deterministic coverage for `PATCH /api/v1/notifications/{notificationId}/read` once the notification fixture source is chosen.
- Add deterministic coverage for `GET /api/v1/reports/programs/{programId}/dashboard-summary` once Playwright can create or obtain program/project membership data through public APIs.
- Add deterministic coverage for `GET /api/v1/public/projects/{projectId}` after project visibility and anonymous-read settings can be toggled through project settings APIs, per the current product direction.
- Keep the committed route baseline at `src/test/resources/backend-route-coverage.tsv` accurate, with `coverageOwner` on every covered row and no owner on `planned` or `planned-high-risk` rows.
- Keep any new tests data-driven and isolated so they create uniquely named data, clean up through public APIs, and do not depend on direct database access.

Done when:

- The committed route baseline has zero ordinary `planned` rows, or each remaining ordinary `planned` row is intentionally blocked by a documented product/API fixture decision.

### 2. Complete High-Risk Asynchronous Playwright API Coverage

Goal: make sure the route-by-route suite covers the surfaces most likely to hide security, tenant-boundary, worker, callback, and durable-state bugs.

Planned work:

- Cover import jobs, import records, import mapping templates, import transform presets, import samples/settings, conflict resolution, review CSV export jobs, and import completion reporting.
- Cover automation rules/jobs/workers, webhook configs/deliveries, email settings/deliveries, worker run/health retention export/prune, and scheduled worker settings.
- Cover AI agent providers, credentials, profiles, repository connections, task lifecycle, dispatch attempts, callback auth, worker token auth, and worker protocol endpoints without invoking paid or unmanaged external execution.
- Cover audit/export/domain-event admin routes with redaction, authorization, artifact-download, and replay/idempotency checks.
- Continue treating import, automation, webhook, email, audit/export/domain-event, system-admin grant/revoke, OAuth/register/logout, and agent execution routes as `planned-high-risk` until their durable side effects and denial paths are covered.

Done when:

- High-risk route baseline rows move from `planned-high-risk` to `covered`, with focused denial/security assertions in addition to happy paths.

### 3. Playwright Frontend Workflow Coverage

Goal: cover the implemented Vite React screens through browser workflows, not only API calls.

Planned work:

- Cover setup, login/logout/current user, project/work item CRUD, collaboration panels, team/planning/board flows, dashboards/reports/saved filters/views, configuration screens, notification preferences/defaults, automation/webhook/email operations, import review/export flows, system-admin/workspace-project-security-policy screens, token screens, and AI agent provider/profile/task/dispatch-attempt screens.
- Prefer stable role/text/test-id selectors and add frontend test IDs only where accessible selectors are not stable enough.
- Exercise realistic cross-screen flows such as creating data through setup, navigating to admin screens, creating work, moving board cards, running workers, creating import artifacts, and inspecting results through the UI.

Done when:

- The major implemented frontend routes have smoke and core-workflow browser tests that fail on broken navigation, broken forms, missing critical data, or visible runtime errors.

### 4. Playwright Full-Stack Security And Regression Coverage

Goal: encode the security audit and production-readiness risks as executable full-stack regression tests.

Planned work:

- Add tests for tenant isolation, authorization denial, CSRF/cookie behavior, token scope enforcement, service-token behavior, system-admin gates, public-read boundaries, redacted API errors, frontend secret masking, attachment/import/export limits, outbound/worker callback authentication surfaces, and no browser access-token local storage.
- Add regression scenarios for import materialization/conflict resolution, queued export artifacts, worker run retention, agent dispatch attempts, dashboard/report rollups, and automation/webhook/email workers.
- Keep long-running scenarios tagged separately from fast smoke checks so CI can run quick gates and scheduled full coverage differently.

Done when:

- The `trasck-test` suite can replace the current manual checklist for broad regression confidence while still leaving exploratory testing for usability issues.

### 5. Human Manual Test And Bug-Fix Loop

Goal: run the now-operable system through a realistic local browser/API pass and fix the defects that only show up when the flows are used together.

Planned work:

- Create fresh local data through setup and the UI, then exercise auth, system admin controls, workspace/project security policy, configuration, work items, planning/boards, dashboards, automation, webhooks, email, imports, export artifacts, audit/export history, and agent dispatch-attempt screens without direct database access.
- Record the exact bugs or missing ergonomics found during the pass, then add focused backend/frontend regression tests for each fixed issue.
- Backfill any issue found in this pass into the Java Playwright suite before considering it fixed.

Done when:

- A local tester can operate core project-management flows, delivery workers, system/security admin flows, import review/export flows, board moves, dashboards, and agent task inspection without touching the database.

### 6. External Worker And Provider Readiness

Goal: prepare the provider-neutral worker boundary for the first non-stub external agent execution without weakening normal Jira/Rally-style product behavior.

Planned work:

- Keep external agent execution disabled by default until security hardening and manual operations testing are complete.
- Validate Codex and Claude Code provider credential UX and errors against the provider-specific credential rules.
- Exercise generic worker claim/heartbeat/log/message/artifact/cancel/retry and webhook-push dispatch against a local worker harness.
- Decide whether the first real execution path should be hosted provider API, backend-managed CLI worker, or a deployable generic worker service once the hardened boundary is proven.

Done when:

- Admins can preview runtime payloads, rotate/revoke credentials, inspect dispatch attempts, and test a local worker without enabling paid or unmanaged external execution.

### 7. Production Security Packaging Follow-Ups

Goal: turn the hardened local/manual-test baseline into deployable production controls.

Planned work:

- Surface Redis-backed throttling readiness in production health checks and deployment observability now that production-like startup already requires a successful Redis ping.
- Design the browser UX/API shape for recent-login or step-up confirmation prompts before hosted system-admin operations.
- Tune the required OWASP Dependency Check gate with NVD API key/cache settings if public scanner throttling becomes noisy.
- Keep secret-bearing deployment config in environment variables and plan the shared Vault/KMS/Secrets Manager abstraction for SMTP, object storage, and non-stub agent credentials.
- Broaden dedicated redaction, tenant-isolation, authorization-denial, outbound URL, attachment/export authorization, and frontend secret-persistence regression coverage.

Done when:

- A production-like deployment can start only with explicit secure configuration, run dependency/security checks predictably, and expose no raw secrets through normal API responses, logs, frontend previews, or generated artifacts.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Use the hardened security baseline from `SECURITY_AUDIT.MD` before broad manual product testing begins, and keep the remaining production-packaging follow-ups tracked before public hosting.
- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, personalization/custom-field paths, automation workers, notification defaults, import parsing/materialization/record editing/history/conflict resolution, and worker history as more APIs are exposed.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, notification defaults, worker runs, import review export jobs, import record versions, clone-retarget, snapshot reruns, and external callback/delivery failures as those flows are added.
- Review dedicated automatic worker-run pruning interval/window defaults after manual worker history grows enough to validate cadence.
- Keep SMTP secrets database-encrypted until the shared external secret-management abstraction is introduced for both SMTP and non-stub agent credentials, and keep all secret-bearing deployment configuration in environment variables rather than checked-in files.
- Manually test a larger real-world import set through the UI, including field diff tables, backend-generated review CSV export artifacts, export artifact downloads, scheduled conflict pickup, and guarded sample job creation, before expanding import update/delete semantics further.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Keep multi-predicate custom-field filtering behind saved-filter/query-builder execution unless product usage proves direct project list query params need it too.
- Continue expanding targeted screen required-field checks only for command endpoints that can invalidate a required field.

### Test Readiness

- Use the Java Playwright `trasck-test` repository as the full-stack automation home for backend API, frontend browser, and cross-system regression coverage.
- Use the committed OpenAPI route coverage baseline, `coverageOwner` column, and generated route inventory to track backend API coverage status until route-by-route Playwright coverage is complete.
- Keep setup bootstrap minimal and use separate opt-in sample-data fixtures for richer browser workflows.
- Keep `TRASCK_E2E_ALLOW_SETUP=true` limited to disposable local stacks; normal reused stacks should continue using explicit login/workspace/project env values.
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
