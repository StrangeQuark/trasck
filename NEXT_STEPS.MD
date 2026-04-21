# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should Redis-backed throttling become mandatory for production-like profiles now, or stay deployment-optional until horizontal scaling is actually enabled?
    - Answer: Make it mandatory now
2. Should system-admin grant/revoke operations require recent-login or step-up confirmation before hosted deployments?
    - Answer: Yes
3. Should workspace security policies remain workspace-wide through manual testing, or should project-level overrides be designed before public project hosting?
    - Answer: Design project-level overrides now
4. Should the next end-to-end product pass be converted into Playwright browser automation immediately, or remain a human-run checklist while UI flows are still changing quickly?
    - Answer: Yes, I added a new Playwright repository to the stack
5. Should the required OWASP Dependency Check gate stay at CVSS 7+, or should production packaging lower the threshold for earlier failure?
    - Answer: Stay at CVSS 7+

## Current State

The backend has the full initial relational schema, Flyway migrations, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, system administration, workspace security policy, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, system-admin/security-policy flows, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered, versioned, diffed, materialized, conflicted, resolved, rerun, completed with guarded open-conflict acceptance, and exercised through checked-in CSV/Jira/Rally sample payload flows. Reusable transform presets have immutable version records, clone-from-version recovery, clone-and-retarget preview/apply, template-local overrides, value lookups, type translations, status translations, materialization snapshots, exact/modified reruns, and conflict-resolution worker pickup.

Import review operations now have backend-generated export-job artifacts for job-level version diffs, conflict-resolution job audit tables, import export-artifact tables, and project/workspace import-completion metrics. The frontend passes current table filter state into queued backend CSV export jobs, lets admins process queued review exports, and records completed artifacts in the shared export-job history/download flow. Smaller tables that do not yet need durable audit artifacts can still use local client-side CSV export.

Import demo job endpoints remain workspace-admin-only. Local/default runtimes default sample jobs on for developer walkthroughs but workspace admins can toggle `sampleJobsEnabled` per workspace through import settings; production-like `prod`, `production`, `staging`, and `hosted` profiles require both the workspace setting and explicit deployment enablement with `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED=true`.

Automation, webhook, email, and import conflict-resolution worker executions write dedicated `automation_worker_runs` and `automation_worker_health` rows. Operations screens can filter run/health previews by worker type. Manual worker-run retention export and pruning accept `workerType`, `triggerType`, `status`, and start-time range filters, so admins can export/prune targeted worker history without deleting unrelated run records. Automatic retention pruning remains all-worker-type by design.

Agent delivery hardening keeps the generic worker protocol as the main extensibility path while Codex and Claude Code adapters expose explicit `stub`, `hosted_api`, and hardened `cli_worker` runtime modes behind the provider-neutral adapter contract. External execution must be intentionally enabled in provider config; CLI-worker config accepts named allowlisted command profiles instead of raw shell commands. Provider runtime preview lets admins inspect non-stub payloads without dispatching work. Dispatch, retry, and cancel boundaries are persisted in first-class `agent_dispatch_attempts` rows, included in `AgentTaskResponse.dispatchAttempts`, and exposed through workspace admin list/export/prune endpoints so admins and tests have a durable view of idempotency keys, payloads, external task IDs, transport, provider type, and failure messages before real external execution is enabled.

Queued import review CSV export jobs can now be picked up by the shared scheduled worker through workspace worker settings. Scheduled and manual review export processing writes `import_review_export` worker run and health rows, and operations screens can filter that worker type alongside automation, webhook, email, and import conflict-resolution workers. Agent dispatch attempt retention now has workspace-level defaults and optional automatic scheduled pruning. Codex and Claude Code provider credential creation now validates provider-specific credential types and basic secret/auth-scheme shape before encrypting credentials.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token/system-admin/security-policy screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent flows, worker retention export/prune/automatic schedule controls, worker-type filtered operations previews, queued backend-generated filtered import review CSV export artifacts with admin processing controls, import diff export artifact history/download controls, import completion metrics tables, agent runtime preview and dispatch-attempt admin controls, and Vitest/Testing Library coverage.

The separate `trasck-test` repository now holds the Java Playwright automation harness for cross-repo testing. It has Maven-based Playwright configuration, environment-backed backend/frontend base URLs, Docker support, API smoke tests for backend health and CSRF metadata, and a browser smoke test for the frontend shell. The next testing work should expand that repo into the primary full-stack regression suite instead of relying on ad hoc manual browser passes.

The full production-minded backend/frontend security audit is recorded in `SECURITY_AUDIT.MD`. The current hardening baseline removes checked-in `.env` files in favor of `.env.example`, rejects unsafe production-like startup defaults, gates Swagger/OpenAPI behind dedicated system-admin access in production-like profiles, exposes system-admin REST/UI management with last-active-admin protection, enables explicit CORS, adds default-deny outbound URL/SSRF policy with exact host, wildcard host, and CIDR allowlisting, tightens password/login protections with default-untrusted `X-Forwarded-For`, supports database-backed or Redis-backed auth throttling while keeping durable auth-failure telemetry, rate-limits callback/worker auth failures, enforces workspace-admin configurable attachment/import/export size and content-type limits, masks frontend secret display, centralizes redacted production API error/log handling while preserving explicit client-error statuses, templates production nginx CSP, makes Java dependency-check a required CI gate with cache/API-key support, keeps frontend `npm audit` in verification, updates frontend container builds to `npm ci`, and adds baseline nginx security headers. The audit still tracks production follow-ups: stronger secret-management/KMS abstraction, deciding whether Redis must be mandatory for production-like deployments, system-admin step-up safeguards, and broader dedicated security regression coverage.


## Primary Next Steps

### 1. Playwright Automation Harness Expansion

Goal: turn the new Java Playwright repository into the stable automation harness used before every manual product pass.

Planned work:

- Add reusable fixtures for setup, login, CSRF, cookie-authenticated browser sessions, Bearer-token API sessions, unique test data generation, and failure diagnostics.
- Add deterministic test environment guidance for running backend, frontend, PostgreSQL, Maildev, Redis, and `trasck-test` together locally and in CI.
- Add artifact capture for traces, screenshots, API response snippets, console errors, and test result reports without leaking secrets.
- Add a top-level smoke suite that verifies backend health, frontend shell, setup readiness, login/current-user behavior, CSRF/cookie behavior, and OpenAPI availability under local profiles.

Done when:

- `trasck-test` can be run with one documented Maven or Docker command against a local Trasck stack and gives clear diagnostics when any service is down or misconfigured.

### 2. Playwright Backend API Coverage

Goal: build API-level Playwright coverage for every backend surface that is already implemented.

Planned work:

- Cover auth, setup, system-admin, workspace security policy, users/tokens, projects, work items, collaboration, attachments, teams, planning, boards, releases, roadmaps, configuration, custom fields/screens, saved filters/views, dashboards, reports, notifications, automation, webhooks, email, imports, exports, audit/activity, domain-event administration, and AI agent endpoints.
- Validate both success paths and important denial paths, including tenant boundaries, project/workspace permissions, token scopes, public read behavior, redaction, upload/export limits, worker authentication, and agent callback authentication.
- Keep API tests data-driven and isolated so they can create fresh data without depending on direct database access.

Done when:

- Every implemented backend route has at least one Playwright API test for its expected local/manual-test behavior and a focused set of negative/security checks.

### 3. Playwright Frontend Workflow Coverage

Goal: cover the implemented Vite React screens through browser workflows, not only API calls.

Planned work:

- Cover setup, login/logout/current user, project/work item CRUD, collaboration panels, team/planning/board flows, dashboards/reports/saved filters/views, configuration screens, notification preferences/defaults, automation/webhook/email operations, import review/export flows, system-admin/security-policy screens, token screens, and AI agent provider/profile/task/dispatch-attempt screens.
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

- Create fresh local data through setup and the UI, then exercise auth, system admin controls, workspace security policy, configuration, work items, planning/boards, dashboards, automation, webhooks, email, imports, export artifacts, audit/export history, and agent dispatch-attempt screens without direct database access.
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

- Decide whether Redis-backed throttling must be mandatory in production-like profiles or only in horizontally scaled deployments.
- Add recent-login or step-up confirmation for system-admin grant/revoke before hosted deployment.
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
