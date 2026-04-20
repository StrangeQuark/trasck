# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should import transformation functions stay as the current fixed allowlist (`trim`, case conversion, `collapse_whitespace`), or should the next import pass add reusable argument-based transform pipelines?
    - Answer: Add reusable argument-based transform pipelines
2. Should automation worker run history be retained indefinitely for now, or should a retention/pruning/export policy be added before scheduled workers are enabled broadly?
    - Answer: Add a retention/pruning/export policy now
3. Should the next board pass add backend swimlane-aware grouping and rank/transition commands before drag/drop UI, or keep grouping client-side until the board UX is designed?
    - Answer: Add backend swimlane-aware grouping and rank/transition commands now
4. Should production SMTP configuration start as application/environment configuration, or should workspace-level email provider settings be modeled from the start while keeping Maildev selectable in non-production?
    - Answer: Go with workspace-level email provider settings from the start

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team and planning APIs are implemented. Workspace admins can create/update/archive teams, manage immediate team membership joins/leaves, keep a single role value per project-team assignment, and manage workspace roadmaps. Project admins can assign teams to projects. Project users can list team/project-team data with read permission. Project board admins can create/update/archive boards, board columns, swimlanes, releases, release scope, project roadmaps, roadmap items, iterations, iteration scope, commitments, closes, and incomplete-work carryover. Boards expose per-column work item card data from configured status mappings, and the browser board detail page renders real cards by column.

Reporting foundations include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. Project, workspace/project-set, and program dashboard summaries are implemented. Persisted snapshot jobs populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with manual run/backfill/reconcile controls, raw snapshot read APIs, report-shaped time-series DTOs, and live/snapshot iteration report APIs. Configurable snapshot retention policies, dedicated cycle-time/iteration/velocity/cumulative-flow rollup tables, a generic experimental rollup table, manual rollup/backfill controls, archive-run metadata, and additive `rollupSeries` project snapshot output are implemented.

Saved dashboard CRUD, widget CRUD, and render APIs are implemented for private, team, project, workspace, and public visibility. Saved filter CRUD, saved-filter work item execution, saved view CRUD, favorites, recent items, and the governed report query catalog are implemented. Workspace, project, and team scoped list endpoints exist for dashboards, saved filters, saved views, and report query catalog entries. Saved-filter execution uses a dedicated cursor-paged endpoint with boolean groups, multiple system/custom-field predicates, typed operator validation, and project-scoped permission coverage. Widget configs can reference saved filters or catalog entries while recursively rejecting raw SQL keys. Report query catalog `parametersSchema` is enforced at dashboard render time, and permission coverage includes project-only, team-member, non-team, and token-scope users for dashboard/filter/catalog/view sharing behavior.

Product configuration APIs are implemented. Workspace admins can manage custom field definitions, contexts, field configurations with project/type overrides, screen metadata, fields, and project/type/operation assignments. Work item editors can set/delete validated custom field values, and project work item lists can use typed searchable custom-field filters. Screen assignments enforce required system/custom fields during work item create/update, and command endpoints that mutate required system fields run targeted checks for the field they changed.

Activity and audit foundations are implemented. Static and configured durable consumers project domain events into cursor-page work-item/project/workspace activity streams, cursor-page workspace audit logs, and configured delivery rows. Tests cover durable outbox failure, retry, dead-letter, disabled consumers, unknown replay consumers, and multi-consumer delivery. Audit retention policy metadata is configurable per workspace; exports write JSON artifacts through configured attachment/object storage and create `export_jobs`, manual/scheduled pruning writes a stored export before deleting eligible rows, and workspace admins can list export jobs, inspect export metadata, and download stored export files.

Automation and integration execution have moved beyond shells. Automation rule execution creates queued jobs; admin worker endpoints process queued/failed jobs and expose succeeded/failed counts. Workspace-level worker settings can enable scheduled automation, webhook, and email workers while preserving manual admin endpoints. Webhook actions create durable delivery rows, and webhook delivery processing supports inspect, retry, cancel, dry-run/real HTTP POST, bounded retry, and `dead_letter` status. Email actions create durable Maildev development-provider delivery rows, with manual/scheduled dry-run or SMTP-backed processing. Worker processing now writes dedicated `automation_worker_runs` history and `automation_worker_health` rows for automation, webhook, and email workers.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import mapping templates can materialize those records into work items through the normal work item service. Mapping templates now support transformation config, reusable value lookups, type translations, and status translations, with REST/JPA coverage and integration tests proving materialization through those rules.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add repository connections, assign work items to agents, receive signed callbacks, create review artifacts/comments, accept agent results, and project agent events into activity/audit streams. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo now has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token creation/listing screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent provider/profile/repository/task flows, selector-fed common IDs, detail routes for configuration/planning/automation/import entities, board card rendering by column, and Vitest/Testing Library route smoke coverage.

## Primary Next Steps

### 1. Backend Reliability And Permission Hardening

Goal: keep the growing backend safe enough for a full manual product test.

Planned work:

- Add migration tests for every new schema change, including import mapping rules and worker history/health.
- Expand permission tests for hierarchy, workflow, collaboration, user-management, team/planning, public read, dashboard/search/personalization, notification defaults, automation workers, import parsing/materialization, worker history/health, and attachment byte APIs.
- Extend activity/audit projection coverage for request-changes, waiting-for-input, retry failures, cancellation failures, import materialization, notification default changes, worker run failures, and external callback/delivery failures.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.

Done when:

- A full backend test run gives useful confidence that core product, reporting, admin, integration, import, worker, and agent flows still align with the spec.

### 2. Import, Worker, And Email Production Hardening

Goal: turn the operational import/delivery slice into a production-ready integration surface.

Planned work:

- Add import conflict handling, update-existing idempotency coverage, mapping-rule validation, and rule-specific audit/activity events.
- Add production SMTP configuration while keeping Maildev selectable only in non-production according to the answered decision.
- Add worker run retention/alerting metadata after the retention question above is answered.
- Add permission, activity, audit, and dead-letter tests around import materialization, email delivery, scheduled workers, retry/cancel failures, and worker settings.

Done when:

- Admins can confidently operate imports and delivery workers without database access, and email delivery has a production configuration path rather than relying on Maildev defaults.

### 3. Frontend Board And Admin Refinement

Goal: move the browser console from a broad manual-test surface toward a usable product workflow.

Planned work:

- Add field-specific child editors for contexts, field configurations, screen fields, screen assignments, board columns, swimlanes, automation conditions/actions, and import mapping rules.
- Add board swimlane grouping after the backend/frontend grouping decision is answered.
- Add board drag/rank and transition interactions after the rank/transition API shape is confirmed for board usage.
- Expand route tests to exercise configuration, planning, automation, import, selector, cookie/CSRF, and global-error surfaces beyond smoke rendering.

Done when:

- A user can operate the configuration/planning/automation/import surfaces from the browser without editing raw JSON for common nested records, then test board cards, swimlanes, and ranking from the browser.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, personalization/custom-field paths, automation workers, notification defaults, import parsing/materialization, and worker history as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, import materialization, notification defaults, worker runs, and external callback/delivery failures as those flows are added.
- Add production SMTP configuration and explicitly confine the Maildev provider to non-production before exposing external email automation broadly.
- Add retention/pruning/export behavior for automation worker runs once the retention policy is chosen.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Keep multi-predicate custom-field filtering behind saved-filter/query-builder execution unless product usage proves direct project list query params need it too.
- Continue expanding targeted screen required-field checks only for command endpoints that can invalidate a required field.

### Test Readiness

- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, notification defaults, automation workers, worker history/health, import materialization, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Expand automation tests for queued delivery retries, dead-letter handling, scheduled workers, worker settings, worker run retention, and Maildev/SMTP email delivery.
- Expand import tests from parser/materialization coverage into mapping conflict, disabled rule behavior, update-existing, idempotency, and audit/activity coverage.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Keep normal browser auth on cookie plus CSRF with no browser access-token local storage.
- Use the route-based frontend shell and global toast/error handling as the manual-test surface.
- Use the checked-in generated TypeScript API client and regenerate it only when the backend contract changes.
- Keep feature-specific frontend API services as the boundary between UI components and the generated client.
- Replace raw JSON child editors with field-specific forms for common nested admin records.
- Expand frontend route tests and replace remaining raw ID inputs only after corresponding list endpoints exist.
