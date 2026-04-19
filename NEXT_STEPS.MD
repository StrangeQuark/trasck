# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should persisted reporting snapshot retention be enforced now with a simple workspace-level retention-days policy, or wait until rollups/archival are designed for long-lived dashboards?
    - Answer: Just design rollups/archival now and implement long-lived dashboards
2. Should audit retention export remain API-response based for now, or should the next hardening pass write export artifacts to configured attachment/object storage before pruning?
    - Answer: Write to storage before pruning
3. Should dashboard, saved-filter, and report-query permission tests add dedicated project-only, team-only, non-member, and token-scope users next, or wait until the frontend sharing workflows clarify the expected UX?
    - Answer: Add the users now
4. Should report query catalog parameter schemas be enforced now with runtime input validation, or remain metadata until the dashboard UI supports parameterized widgets?
    - Answer: Enforce now with runtime input validation

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team and planning APIs are implemented. Workspace admins can create/update/archive teams, manage immediate team membership joins/leaves, and keep a single role value per project-team assignment. Project admins can assign teams to projects. Project users can list team/project-team data with read permission. Project board admins can create/update/cancel iterations, add/remove scoped work items, commit iteration points from current scope, close iterations, and carry incomplete work into a target iteration.

Reporting foundations now include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. Project, workspace/project-set, and program dashboard summaries are implemented. Persisted snapshot jobs populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with manual run/backfill/reconcile controls, raw snapshot read APIs, report-shaped time-series DTOs, and live/snapshot iteration report APIs. Saved dashboard CRUD, widget CRUD, and render APIs are implemented for private, team, project, workspace, and public visibility. Saved filter CRUD and the governed report query catalog are implemented, and widget configs can reference saved filters or catalog entries while recursively rejecting raw SQL keys.

Activity and audit foundations are implemented. Static and configured durable consumers project domain events into work-item/project/workspace activity streams, workspace audit logs, and configured delivery rows. Tests cover durable outbox failure, retry, dead-letter, disabled consumers, unknown replay consumers, and multi-consumer delivery. Audit retention policy metadata is configurable per workspace; retention candidates can be exported through an admin API response, eligible rows can be pruned manually, and scheduled pruning is wired.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add repository connections, assign work items to agents, receive signed callbacks, create review artifacts/comments, accept agent results, and project agent events into activity/audit streams. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution.

Local runtime polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed.

## Primary Next Steps

### 1. Frontend API Contract Preparation

Goal: make the backend easier to consume from the Vite React frontend.

Planned work:

- Add typed API examples or generated OpenAPI coverage for setup, auth, work items, teams, planning, reporting, dashboards, saved filters, report query catalog, attachments, audit/admin, and agents.
- Make list endpoints consistently pageable or explicitly documented as small configuration lists.
- Review DTO names and response shapes for frontend ergonomics before UI work starts.
- Add frontend-facing examples for common flows: setup, login, project work-item list, work-item detail, dashboard render, saved filter creation, and agent assignment.

Done when:

- The frontend can build against stable request/response contracts instead of reading backend code directly.

### 2. Dashboard And Reporting Refinement

Goal: tighten the reporting surface now that dashboards, saved filters, catalog queries, and persisted snapshots exist.

Planned work:

- Add dedicated permission matrix tests for private, team, project, workspace, public, non-member, and token-scope dashboard/filter/query-catalog cases.
- Decide and implement snapshot retention or rollup policy if we want automatic pruning before large manual testing.
- Decide whether audit retention exports should stay API-response based or write durable export artifacts before pruning.
- Add runtime parameter validation for report query catalog entries if parameterized widgets are needed before the frontend dashboard builder.

Done when:

- Dashboard/reporting APIs have clear sharing semantics, retention behavior, and frontend-ready query contracts.

### 3. Manual Test Readiness Polish

Goal: remove avoidable friction before a full end-to-end manual pass.

Planned work:

- Expand HTTP examples to cover the new reporting/dashboard/audit endpoints.
- Add repository/query tests for high-use list screens and detail pages.
- Review logging defaults so local test output is useful without overwhelming normal backend runs.
- Add a concise manual smoke-test script that walks setup, auth, project configuration, work item lifecycle, dashboard rendering, and agent lifecycle.

Done when:

- A user can start Docker, run the backend, and manually exercise the core product without needing to infer hidden setup steps.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Snapshot retention, rollup, and archival policy.
- Runtime parameter validation for governed report query catalog entries.
- Saved views, favorites, and recent item APIs.
- Broader dashboard permission matrix coverage across sharing scopes and token scopes.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, and agent endpoints as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Decide whether audit retention export needs object-storage artifacts before pruning.
- Decide whether reporting snapshots need retention/rollup policy before long-running workspace tests.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide when to introduce configurable custom fields, screens, and screen assignments beyond the current core system field model.

### Test Readiness

- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard sharing, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Add migration tests whenever the schema changes.
- Add repository/query tests for list screens and detail pages.
- Add automation tests once the first automation executor exists.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Add broader reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Connect the Vite React app to the backend through a typed API client.
- Add a local environment configuration for backend base URL.
- Build first frontend flows for setup/login, project work item list/detail, team/planning configuration, dashboard rendering, saved filters, and agent assignment/review.
