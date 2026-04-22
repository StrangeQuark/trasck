# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should production-profile/admin-gating Playwright coverage start by launching a separate production-like backend stack from the test suite, or should it target an already-running production-like stack through explicit `TRASCK_*` environment variables first?
    - Answer: Launch a separate production-like backend stack from the test suite
2. Should the next real-backend browser workflow expansion be split into feature-family tests for imports, automation, agents, and workspace/project admin, or should it build one longer cross-feature manual-test rehearsal?
    - Answer: Do both, build the feature-family tests and also implement one longer cross-feature manual-test rehearsal
3. Should per-webhook previous-secret overlap allow an upper bound such as 30 days before external customer testing, or should admins be allowed to choose any positive duration for now?
    - Answer: Give admins control, default to 30 days

## Current State

The backend has the full initial relational schema, Flyway migrations, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project, public work-item, public work-item comment, and public work-item attachment reads are wired through security and checked against workspace anonymous-read plus project/work-item visibility in domain code; those public-read switches can now be changed through workspace/project security-policy APIs and the frontend settings routes. Anonymous public comments use narrow DTOs that omit author IDs and edit metadata, private comments are excluded, public attachment DTOs omit storage/uploader internals, and attachment bytes are retrieved through short-lived app-signed backend download URLs. App-signed backend proxy URLs are the planned long-term public attachment download pattern, including for S3-compatible storage.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership, pending invitations can be revoked through a public workspace-admin API, and workspace-admin-created users can be removed from a workspace without direct database access. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

Workspace and project role management is now first-class. Admins can list permission catalogs, inspect role details, create custom roles, update role metadata, preview permission changes against impacted members/invitations, save permission changes only with a fresh confirmation token, archive unassigned roles, view immutable role version history, and rollback to earlier role versions. Seeded roles can be edited through the same APIs while last-admin constraints prevent removing the only route to workspace or project administration, and archived roles cannot be assigned through invitation, user, token, or agent setup flows.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list/delete, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, program/portfolio, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, system administration, workspace/project security policy, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, system-admin flows, a Workspace Settings route that now hosts workspace security policy, member/invitation management, and full workspace role management, a Project Settings route with project security policy, public-read effective state, full project role management, and an anonymous preview link, a Programs route for program CRUD/project assignment/dashboard summary review, a dedicated anonymous public project/work-item/comment/attachment preview route with signed public attachment downloads, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered, versioned, diffed, materialized, conflicted, resolved, rerun, completed with guarded open-conflict acceptance, and exercised through checked-in CSV/Jira/Rally sample payload flows. Reusable transform presets have immutable version records, clone-from-version recovery, clone-and-retarget preview/apply, template-local overrides, value lookups, type translations, status translations, materialization snapshots, exact/modified reruns, and conflict-resolution worker pickup.

Import review operations now have backend-generated export-job artifacts for job-level version diffs, conflict-resolution job audit tables, import export-artifact tables, and project/workspace import-completion metrics. The frontend passes current table filter state into queued backend CSV export jobs, lets admins process queued review exports, and records completed artifacts in the shared export-job history/download flow. Smaller tables that do not yet need durable audit artifacts can still use local client-side CSV export.

Import demo job endpoints remain workspace-admin-only. Local/default runtimes default sample jobs on for developer walkthroughs but workspace admins can toggle `sampleJobsEnabled` per workspace through import settings; production-like `prod`, `production`, `staging`, and `hosted` profiles require both the workspace setting and explicit deployment enablement with `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED=true`.

Automation, webhook, email, and import conflict-resolution worker executions write dedicated `automation_worker_runs` and `automation_worker_health` rows. Operations screens can filter run/health previews by worker type. Manual worker-run retention export and pruning accept `workerType`, `triggerType`, `status`, and start-time range filters, so admins can export/prune targeted worker history without deleting unrelated run records. Automatic retention pruning remains all-worker-type by design. Webhook secrets are stored encrypted for signing and as hashes for redacted configured-state checks; real outbound webhook deliveries include `X-Trasck-Webhook-Id`, `X-Trasck-Webhook-Delivery-Id`, `X-Trasck-Webhook-Timestamp`, `X-Trasck-Webhook-Signature-Key-Id`, and `X-Trasck-Webhook-Signature` headers using HMAC-SHA256 over `timestamp + "." + body`. Webhook secret writes rotate the non-secret `secretKeyId`, retain the immediately previous encrypted secret/key/hash for a per-webhook configurable positive overlap window stored as `previousSecretOverlapSeconds`, default new webhooks to 72 hours through `TRASCK_WEBHOOK_PREVIOUS_SECRET_OVERLAP`, expose previous-key expiry metadata, and store the selected `signatureKeyId` on each queued delivery so old queued deliveries can still be signed during rotation overlap. Receiver verification/rotation guidance is documented in `docs/WEBHOOK_SIGNATURES.md`. External Java Playwright coverage now exercises automation rules/jobs/workers, webhook configuration/deliveries, signed real local-receiver webhook processing including dual-secret rotation overlap, Maildev dry-run email delivery processing, worker settings, worker run/health reads, and worker run retention export/prune.

Agent delivery hardening keeps the generic worker protocol as the main extensibility path while Codex and Claude Code adapters expose explicit `stub`, `hosted_api`, and hardened `cli_worker` runtime modes behind the provider-neutral adapter contract. External execution must be intentionally enabled in provider config; CLI-worker config accepts named allowlisted command profiles instead of raw shell commands. Provider runtime preview lets admins inspect non-stub payloads without dispatching work. Dispatch, retry, and cancel boundaries are persisted in first-class `agent_dispatch_attempts` rows, included in `AgentTaskResponse.dispatchAttempts`, and exposed through workspace admin list/export/prune endpoints so admins and tests have a durable view of idempotency keys, payloads, external task IDs, transport, provider type, and failure messages before real external execution is enabled. Repository connections can now be deactivated through the public workspace admin API so test and admin flows can clean up connection records without direct database access.

Queued import review CSV export jobs can now be picked up by the shared scheduled worker through workspace worker settings. Scheduled and manual review export processing writes `import_review_export` worker run and health rows, and operations screens can filter that worker type alongside automation, webhook, email, and import conflict-resolution workers. Agent dispatch attempt retention now has workspace-level defaults and optional automatic scheduled pruning. Codex and Claude Code provider credential creation now validates provider-specific credential types and basic secret/auth-scheme shape before encrypting credentials.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client that is refreshed after backend endpoint batches, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token/system-admin/workspace-security-policy/project-security-policy/workspace member-management screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent flows, worker retention export/prune/automatic schedule controls, worker-type filtered operations previews, queued backend-generated filtered import review CSV export artifacts with admin processing controls, import diff export artifact history/download controls, import completion metrics tables, agent runtime preview and dispatch-attempt admin controls, and Vitest/Testing Library coverage.

The separate `trasck-test` repository now holds the Java Playwright automation harness for cross-repo testing. It has Maven-based Playwright configuration, environment-backed backend/frontend base URLs, optional authenticated smoke credentials plus workspace/project IDs, optional disposable first-run setup bootstrap through `/api/v1/setup`, separate opt-in sample-data fixture support plus direct per-test seed creation for real-backend browser workflows, Docker support, reusable API/browser fixtures, unique-data helpers, a local HTTP receiver for webhook/worker delivery assertions, redacted API snippets, committed OpenAPI route coverage baseline enforcement with `coverageOwner` on covered rows, generated OpenAPI route inventory output, screenshots, traces, deterministic local-stack guidance, API smoke tests for backend health, CSRF metadata, setup endpoint reachability, setup first-run-only conflict behavior, unauthenticated current-user denial, optional login/current-user cookie and Bearer sessions, local OpenAPI availability, authenticated API coverage for workspace/project security policy and public project/work-item/comment/attachment read boundaries with signed public attachment downloads, personal and service API token scope/redaction/revocation behavior, system-admin/audit/export reads and mutation coverage, domain-event replay coverage, configuration/custom-field/screen/report-query/reporting-retention CRUD, work item collaboration/activity and command routes, attachment byte upload/download, workspace and work-item label cleanup, team/membership/planning/board/swimlane/release/roadmap resources, reporting history/snapshot reads and snapshot execution controls, program CRUD/project assignment/dashboard summary coverage, team-scoped dashboard/filter/view/report-query lists, repository connections, invitation list/cancellation, workspace-admin human user list/creation/removal, full workspace/project role management API coverage, notification preference/default CRUD plus direct and automation-created notification read coverage, automation async rule/job/webhook/email/worker API coverage with dual-secret webhook rotation assertions, high-risk import lifecycle/review-export/report API coverage, auth/logout/register/OAuth unsafe-input coverage, provider-neutral agent provider/profile/credential/task/callback/worker-token/dispatch-attempt API coverage, dashboards/widgets, saved filters, saved views, favorites, recent items, resource cleanup through public APIs where public cleanup APIs exist, browser smoke tests for the frontend shell, Workspace Settings member/invitation flow, program/portfolio management, automation async worker controls, auth/token/system-admin controls, import review/export worker controls, agent provider/profile/repository/task/dispatch-attempt controls, workspace/project public-read settings, and public project/work-item/comment/attachment preview route, and a real-backend seeded browser workflow that signs in through the UI, verifies browser local storage does not contain an access token, loads/creates project work, executes saved filters, renders dashboards, creates a team, and creates a custom field. The committed baseline currently tracks 397 covered backend routes, 0 ordinary planned routes, and 0 planned high-risk routes.

The full production-minded backend/frontend security audit is recorded in `SECURITY_AUDIT.MD`. The current hardening baseline removes checked-in `.env` files in favor of `.env.example`, rejects unsafe production-like startup defaults, requires Redis-backed auth throttling in production-like profiles and pings Redis on startup, gates Swagger/OpenAPI behind dedicated system-admin access in production-like profiles, exposes system-admin REST/UI management with last-active-admin protection and production-like recent-login step-up for grant/revoke, enables explicit CORS, adds default-deny outbound URL/SSRF policy with exact host, wildcard host, and CIDR allowlisting, tightens password/login protections with default-untrusted `X-Forwarded-For`, rate-limits callback/worker auth failures, enforces workspace-admin and project-admin configurable attachment/import/export size and content-type limits, masks frontend secret display, centralizes redacted production API error/log handling while preserving explicit client-error statuses, templates production nginx CSP, makes Java dependency-check a required CI gate with cache/API-key support, keeps frontend `npm audit` in verification, updates frontend container builds to `npm ci`, and adds baseline nginx security headers. The audit still tracks production follow-ups: stronger secret-management/KMS abstraction, system-admin step-up UX hardening, production deployment health/observability around Redis-backed throttling, and broader dedicated security regression coverage.


## Primary Next Steps

### 1. Playwright Production Startup And Admin-Gating Security Coverage

Goal: encode the production-startup and system-admin gating risks as executable regression coverage before manual testing.

Planned work:

- Add Java Playwright coverage for production-like startup expectations where feasible, including required secure config, Redis-backed throttling readiness, and Swagger/OpenAPI access restrictions.
- Expand system-admin gate tests around list/grant/revoke, last-active-admin protection, recent-login step-up expectations, and non-admin denial.
- Keep browser checks for system-admin controls and no browser access-token local storage aligned with backend API checks.
- Decide whether the suite should launch a production-like backend stack itself or target an already-running production-like stack through explicit environment variables.

Done when:

- The highest-risk production startup and admin-gating failures are caught by repeatable backend/API/browser tests without relying on a manual checklist.

### 2. Playwright Real Frontend Workflow Expansion

Goal: continue moving frontend browser coverage from route smoke checks toward real-backend workflows with seeded data.

Planned work:

- Extend the new real-backend browser workflow beyond UI login, work item load/create, saved-filter execution, dashboard render, team creation, and custom-field creation.
- Add real workflow coverage for workspace/project admin, imports, automation/webhook/email workers, agent provider/profile/task flows, notification preferences/defaults, and deeper board/release/roadmap actions.
- Keep mocked browser tests for fast UI assertions where they are still useful, but prefer real backend flows for manual-test readiness.
- Use API-created seed data and public API cleanup rather than direct database access.

Done when:

- The major implemented frontend routes have real workflow coverage that fails on broken navigation, broken forms, missing critical data, visible runtime errors, or broken backend integration.

### 3. Human Manual Test And Bug-Fix Loop

Goal: run the now-operable system through a realistic local browser/API pass and fix the defects that only show up when the flows are used together.

Planned work:

- Create fresh local data through setup and the UI, then exercise auth, system admin controls, workspace/project security policy, configuration, work items, planning/boards, dashboards, automation, webhooks, email, imports, export artifacts, audit/export history, and agent dispatch-attempt screens without direct database access.
- Record the exact bugs or missing ergonomics found during the pass, then add focused backend/frontend regression tests for each fixed issue.
- Backfill any issue found in this pass into the Java Playwright suite before considering it fixed.

Done when:

- A local tester can operate core project-management flows, delivery workers, system/security admin flows, import review/export flows, board moves, dashboards, and agent task inspection without touching the database.

### 4. External Worker And Provider Readiness

Goal: prepare the provider-neutral worker boundary for the first non-stub external agent execution without weakening normal Jira/Rally-style product behavior.

Planned work:

- Keep external agent execution disabled by default until security hardening and manual operations testing are complete.
- Validate Codex and Claude Code provider credential UX and errors against the provider-specific credential rules.
- Exercise generic worker claim/heartbeat/log/message/artifact/cancel/retry and webhook-push dispatch against a local worker harness.
- Decide whether the first real execution path should be hosted provider API, backend-managed CLI worker, or a deployable generic worker service once the hardened boundary is proven.

Done when:

- Admins can preview runtime payloads, rotate/revoke credentials, inspect dispatch attempts, and test a local worker without enabling paid or unmanaged external execution.

### 5. Production Security Packaging Follow-Ups

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
- Keep the committed OpenAPI route coverage baseline, `coverageOwner` column, and generated route inventory as the contract gate for new backend routes now that the current route set is fully marked `covered`.
- Keep setup bootstrap minimal and use API-created seed data plus cleanup for real-backend browser workflows; retain separate opt-in sample-data fixtures for tests that explicitly request reusable sample context.
- Keep `TRASCK_E2E_ALLOW_SETUP=true` limited to disposable local stacks; normal reused stacks should continue using explicit login/workspace/project env values.
- Add a dedicated security regression suite mapped to `SECURITY_AUDIT.MD` for tenant isolation, permission denial, public-read boundaries including signed public attachment downloads, token/callback validation, production secret/config validation, Swagger/OpenAPI gating, CSRF/cookie behavior, CORS expectations, outbound URL denial, redaction, attachment/export access, and frontend token/secret persistence.
- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, notification defaults, automation workers, worker history/health, import materialization/conflicts/record editing/history, agent dispatch attempts, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Expand automation tests for queued delivery retries after previous-secret overlap expiry, dead-letter handling, scheduled workers, worker settings, scheduled import review export pickup, manual and automatic worker run retention schedule/window/throttle behavior, worker-type/trigger/status/date-range retention filters, SMTP provider settings, and Maildev/SMTP email delivery.
- Expand import tests beyond route coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, conflict update semantics, production-profile sample gating, larger real-world fixture sizes, and field-specific import review UI assertions.
- Expand agent adapter contract tests covering provider-specific credential validation, automatic dispatch-attempt retention pruning, callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, dispatch attempts, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Keep normal browser auth on cookie plus CSRF with no browser access-token local storage.
- Use the route-based frontend shell and global toast/error handling as the manual-test surface.
- Use the checked-in generated TypeScript API client and regenerate it only when the backend contract changes.
- Keep feature-specific frontend API services as the boundary between UI components and the generated client.
- Use the full role-management screens in Workspace Settings and Project Settings for seeded/custom role maintenance, impacted-member previews, version history, and rollback during manual admin testing. Program/portfolio management now has a frontend route, public-read settings live in Workspace Settings and Project Settings, public project preview includes public work items, comments, and signed public attachment downloads, and Workspace member/invitation management uses managed role picker APIs.
- Keep direct workspace-admin notification creation API/test-fixture-only until notification center UX is expanded.
- Replace remaining raw JSON child editors with field-specific forms for common nested admin/import records.
- Expand frontend route tests and replace remaining raw ID inputs only after corresponding list endpoints exist.
- Keep board drag/drop card-level insertion covered in route/component tests as the board UI grows.
- Exercise import sample-data walkthroughs, import workspace sample settings, field-specific import diff tables, column filters, backend-generated review CSV export jobs, export artifact downloads, and import completion dashboard screens during manual end-to-end testing.
