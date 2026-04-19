# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should screen required-field enforcement remain limited to work item create/update, or should command endpoints that mutate required fields also run the relevant screen checks?
    - Answer: Expand enforcement to command endpoints that mutate required fields, but keep it targeted to the field being changed
2. Should custom-field work-item list filtering support multiple custom-field predicates in one request now, or wait for saved-filter/query-builder execution?
    - Answer: Wait for the saved-filter/query-builder execution, but add steps to do this in the Primary Next Steps section so we don't miss it
3. Should the generated frontend client be wrapped immediately in feature-specific hooks/services, or should the first screens call `TrasckApiClient.request` directly until UI flows settle?
    - Answer: Wrap immediately in feature-specific hooks/services
4. Should committed `src/api/generated/openapi.json` stay as the reviewed contract snapshot, or should only the generated TypeScript client be committed after first frontend integration proves enough?
    - Answer: Keep both for now

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team and planning APIs are implemented. Workspace admins can create/update/archive teams, manage immediate team membership joins/leaves, and keep a single role value per project-team assignment. Project admins can assign teams to projects. Project users can list team/project-team data with read permission. Project board admins can create/update/cancel iterations, add/remove scoped work items, commit iteration points from current scope, close iterations, and carry incomplete work into a target iteration.

Reporting foundations include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. Project, workspace/project-set, and program dashboard summaries are implemented. Persisted snapshot jobs populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with manual run/backfill/reconcile controls, raw snapshot read APIs, report-shaped time-series DTOs, and live/snapshot iteration report APIs. Configurable snapshot retention policies, dedicated cycle-time/iteration/velocity/cumulative-flow rollup tables, a generic experimental rollup table, manual rollup/backfill controls, archive-run metadata, and additive `rollupSeries` project snapshot output are implemented.

Saved dashboard CRUD, widget CRUD, and render APIs are implemented for private, team, project, workspace, and public visibility. Saved filter CRUD, saved view CRUD, favorites, recent items, and the governed report query catalog are implemented. Workspace, project, and team scoped list endpoints exist for dashboards, saved filters, saved views, and report query catalog entries. Widget configs can reference saved filters or catalog entries while recursively rejecting raw SQL keys. Report query catalog `parametersSchema` is enforced at dashboard render time, and permission coverage includes project-only, team-member, non-team, and token-scope users for dashboard/filter/catalog/view sharing behavior.

Product configuration APIs are implemented for the first backend pass. Workspace admins can manage custom field definitions and contexts, work item editors can set/delete validated custom field values, project work-item lists can use typed searchable custom-field filters, and workspace admins can manage screen metadata, fields, and project/type/operation assignments. Screen assignments now enforce required system/custom fields during work item create/update. Field configuration CRUD remains. Export jobs intentionally remain list/get/download until a second export type exists; generic create/cancel/retry should be added at that point.

Activity and audit foundations are implemented. Static and configured durable consumers project domain events into cursor-page work-item/project/workspace activity streams, cursor-page workspace audit logs, and configured delivery rows. Tests cover durable outbox failure, retry, dead-letter, disabled consumers, unknown replay consumers, and multi-consumer delivery. Audit retention policy metadata is configurable per workspace; exports write JSON artifacts through configured attachment/object storage and create `export_jobs`, manual/scheduled pruning writes a stored export before deleting eligible rows, and workspace admins can list export jobs, inspect export metadata, and download stored export files.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add repository connections, assign work items to agents, receive signed callbacks, create review artifacts/comments, accept agent results, and project agent events into activity/audit streams. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo now has `npm run generate:api`, checked-in generated OpenAPI JSON, and a generated `TrasckApiClient` with auth, CSRF, binary response, and cursor-page helpers.

## Primary Next Steps

### 1. Frontend First Flows

Goal: connect the Vite React shell to the backend through the checked-in generated API client.

Planned work:

- Add local environment config for the backend base URL.
- Build a thin frontend API module around `TrasckApiClient` based on the answer above.
- Implement setup/login/current-user flows.
- Implement project work item cursor list and detail read flows.
- Implement dashboard render and agent assignment read/action calls after the first auth/work-item flow works.

Done when:

- The frontend can authenticate, show a project work-item list, open a work-item detail page, and call at least one reporting/dashboard endpoint through typed API functions.

### 2. Remaining Backend Configuration Hardening

Goal: close the remaining backend configuration gaps that are better finished after the first frontend/API contract pass exposes real usage.

Planned work:

- Add field configuration CRUD.
- Decide and implement multi-predicate custom-field filtering if it should land before saved-filter/query-builder execution.
- Decide whether screen required-field enforcement should expand beyond work item create/update command paths.
- Add board, release, roadmap, notification, automation, and import API slices.
- Add generic export create/cancel/retry once a second export type exists.

Done when:

- The remaining backend configuration APIs have service-layer permission checks, targeted integration coverage, and a clear contract for frontend screens.

### 3. Backend Reliability And Permission Hardening

Goal: keep the growing backend safe enough for manual product testing.

Planned work:

- Add migration tests for every new schema change.
- Expand permission tests for hierarchy, workflow, collaboration, user-management, team/planning, public read, dashboard/search/personalization, and attachment byte APIs.
- Extend activity/audit projection coverage for request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.

Done when:

- A full backend test run gives useful confidence that core product, reporting, admin, and agent flows still align with the spec.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, and the latest personalization/custom-field paths as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add field configuration CRUD.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide whether multi-predicate custom-field filters belong in the project work-item list endpoint or only in saved-filter/query-builder execution.
- Decide whether screen required-field enforcement should stay on create/update or cover mutation-specific command endpoints as well.

### Test Readiness

- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Add automation tests once the first automation executor exists.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Use the checked-in generated TypeScript API client and regenerate it only when the backend contract changes.
- Add a local environment configuration for backend base URL.
- Build first frontend flows for setup/login, project work item list/detail, team/planning configuration, dashboard rendering, saved filters/views, and agent assignment/review.
