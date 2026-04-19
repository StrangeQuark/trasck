# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should custom field and screen APIs start with custom field definitions, contexts, and value CRUD first, or should they start with screen layout/screen assignment APIs first?
    - Answer: Start with custom field definitions, contexts, and value CRUD first
2. Should custom field values become searchable/filterable in the first custom-field API pass, or should search integration wait until the field definition and value APIs are stable?
    - Answer: Implement search/filter now
3. Should saved views, favorites, and recent items share one personalization API/package, or should each be implemented as a separate feature surface?
    - Answer: Share one API/package
4. Should export jobs add generic create/cancel/retry admin endpoints before import APIs exist, or should they remain list/get/download while export-type-specific APIs create jobs?
    - Answer: Remain list/get/download for now, but make a note in the NEXT_STEPS.MD file to expand this in the future

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team and planning APIs are implemented. Workspace admins can create/update/archive teams, manage immediate team membership joins/leaves, and keep a single role value per project-team assignment. Project admins can assign teams to projects. Project users can list team/project-team data with read permission. Project board admins can create/update/cancel iterations, add/remove scoped work items, commit iteration points from current scope, close iterations, and carry incomplete work into a target iteration.

Reporting foundations include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. Project, workspace/project-set, and program dashboard summaries are implemented. Persisted snapshot jobs populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with manual run/backfill/reconcile controls, raw snapshot read APIs, report-shaped time-series DTOs, and live/snapshot iteration report APIs. Configurable snapshot retention policies, dedicated cycle-time/iteration/velocity/cumulative-flow rollup tables, a generic experimental rollup table, manual rollup/backfill controls, archive-run metadata, and additive `rollupSeries` project snapshot output are implemented.

Saved dashboard CRUD, widget CRUD, and render APIs are implemented for private, team, project, workspace, and public visibility. Saved filter CRUD and the governed report query catalog are implemented. Workspace, project, and team scoped list endpoints exist for dashboards, saved filters, and report query catalog entries. Widget configs can reference saved filters or catalog entries while recursively rejecting raw SQL keys. Report query catalog `parametersSchema` is enforced at dashboard render time, and permission coverage includes project-only, team-member, non-team, and token-scope users for dashboard/filter/catalog sharing behavior.

Activity and audit foundations are implemented. Static and configured durable consumers project domain events into cursor-page work-item/project/workspace activity streams, cursor-page workspace audit logs, and configured delivery rows. Tests cover durable outbox failure, retry, dead-letter, disabled consumers, unknown replay consumers, and multi-consumer delivery. Audit retention policy metadata is configurable per workspace; exports write JSON artifacts through configured attachment/object storage and create `export_jobs`, manual/scheduled pruning writes a stored export before deleting eligible rows, and workspace admins can list export jobs, inspect export metadata, and download stored export files.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add repository connections, assign work items to agents, receive signed callbacks, create review artifacts/comments, accept agent results, and project agent events into activity/audit streams. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed.

## Primary Next Steps

### 1. Product Configuration Expansion

Goal: fill the remaining Jira/Rally-style configuration and personalization surfaces before broad manual testing.

Planned work:

- Add custom field definitions, field contexts/configurations, custom field value APIs, screen layouts, screen tabs/fields, and screen assignment APIs.
- Add saved views, favorites, and recent item APIs with permission checks and tests.
- Keep custom fields compatible with current work item create/update/detail/list, dashboard, saved filter, report query, and agent context surfaces.
- Decide how soon custom fields become searchable/filterable in work item lists and saved filters.

Done when:

- Custom fields/screens and saved views/favorites/recent items can be created, listed, updated, deleted or archived, permission-tested, and referenced from relevant work item or reporting APIs.

### 2. Generic Admin Import/Export Surface

Goal: make export jobs the first generic admin data movement surface while keeping existing audit export/prune flows stable.

Planned work:

- Add generic export-job admin affordances around existing `export_jobs` records.
- Decide whether create/cancel/retry should be generic now or remain type-specific until import APIs exist.
- Keep audit retention export/prune behavior intact and reuse the stored attachment/object artifact download path.
- Prepare import job APIs to share the same admin status, artifact, and failure-report patterns.

Done when:

- Workspace admins have a coherent generic export-job surface beyond audit-specific entry points, without weakening retention/prune safety.

### 3. OpenAPI Client Generation And Contract Stabilization

Goal: make the frontend consume the backend through a generated, reviewed contract instead of duplicated handwritten types.

Planned work:

- Add generation tooling against backend `/v3/api-docs`.
- Check the generated TypeScript API client into the Vite React repo when frontend work begins.
- Normalize generated client auth, CSRF, binary download, and cursor-page helpers.
- Keep `docs/api/FRONTEND_API_CONTRACT.md` as the human-readable guide alongside generated types.

Done when:

- The frontend has a repeatable way to regenerate typed API calls from the backend OpenAPI document.

### 4. Frontend First Flows

Goal: connect the Vite React shell to the backend through typed calls.

Planned work:

- Add local environment config for the backend base URL.
- Implement setup/login/current-user flows.
- Implement project work item cursor list and detail read flows.
- Implement dashboard render and agent assignment read/action calls after the first auth/work-item flow works.

Done when:

- The frontend can authenticate, show a project work-item list, open a work-item detail page, and call at least one reporting/dashboard endpoint through typed API functions.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.
- Saved views, favorites, and recent item APIs.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, and agent endpoints as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add custom fields/screens plus saved views/favorites/recent items.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide when to introduce configurable custom fields, screens, and screen assignments beyond the current core system field model.

### Test Readiness

- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard sharing, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Add automation tests once the first automation executor exists.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Before frontend development starts, generate the TypeScript API client from backend `/v3/api-docs`, check the generated client into the Vite React repo, and regenerate only when the backend contract changes.
- Add a local environment configuration for backend base URL.
- Build first frontend flows for setup/login, project work item list/detail, team/planning configuration, dashboard rendering, saved filters, and agent assignment/review.
