# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should persisted reporting snapshot read APIs expose raw snapshot records first, report-shaped time-series DTOs first, or both in one pass?
    - Answer: Both in one pass
2. Should saved filters be private/workspace/team shareable like dashboards, or project-scoped first because they primarily feed work item lists?
    - Answer: Make saved filters use the same visibility model as dashboards, but allow optional project scoping inside the filter query. That gives us both flexibility and project-focused UX
3. Should dashboard sharing add project-scoped dashboards before the frontend dashboard surface, or are private/workspace/team dashboards enough for now?
    - Answer: Add project-scoped dashboards now
4. Should flexible dashboard widgets stay limited to reporting-API-backed query config, or should the next reporting pass add a governed custom query catalog for power users?
    - Answer: Add a governed custom query catalog
5. Should iteration reports calculate commitment/carryover from live iteration/work-item state first, or read from persisted iteration and velocity snapshots by default?
    - Answer: Allow for both

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired. Work items now support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project. Dedicated team assignment command, history, and events are implemented through `POST /api/v1/work-items/{workItemId}/team`, `work_item_team_history`, `work_item.team_changed`, and `GET /api/v1/reports/work-items/{workItemId}/team-history`. Comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, work-log self-service uses dedicated own-entry permissions, and these APIs publish durable domain events with HTTP integration coverage.

Team and planning APIs are implemented. Workspace admins can create/update/archive teams, manage immediate team membership joins/leaves, and keep a single role value per project-team assignment. Project admins can assign teams to projects. Project users can list team/project-team data with read permission. Project board admins can create/update/cancel iterations, add/remove scoped work items, commit iteration points from current scope, close iterations, and carry incomplete work into a target iteration. Team and iteration domain events are projected into activity and audit streams, and the HTTP integration test covers permission denial, explicit team assignment, iteration scope, reporting summary compatibility, commitment, close, and carryover.

Reporting foundations now include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. The on-demand project dashboard summary API is implemented at `GET /api/v1/reports/projects/{projectId}/dashboard-summary`. It supports exact UTC `from`/`to` filters, project/team/iteration scopes, explicit team assignment with membership-snapshot fallback, non-deleted work-log totals, work counts, throughput, estimate/time totals, aging WIP, cycle-time inputs, status/type/priority groupings, and widget-shaped response data. Workspace/project-set and program dashboard summaries are implemented at `GET /api/v1/reports/workspaces/{workspaceId}/dashboard-summary` and `GET /api/v1/reports/programs/{programId}/dashboard-summary`; their first response shape exposes project, team, and work item type dimensions together and orders project comparisons by earliest `workspace_sequence_number`. Persisted snapshot jobs now populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with a manual trigger at `POST /api/v1/reports/workspaces/{workspaceId}/snapshots/run`; dashboard cycle-time output uses `cycle_time_records` when available. Saved dashboard CRUD, widget CRUD, and render APIs are implemented for private, workspace-shared, and team-shared dashboards. Flexible widget config is reporting-API-backed and rejects raw SQL.

Activity and audit foundations are implemented. Static durable consumers project domain events into work-item/project/workspace activity streams and workspace audit logs. Audit retention policy metadata is configurable per workspace. Admin replay can reset and dispatch selected durable consumers, defaulting to activity and audit projection. Activity/audit projection tests cover visibility, redaction, and idempotent replay.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add generic Git/GitHub/GitLab repository connections, assign work items to agents, receive per-provider RS256/JWKS-style signed callbacks, create review artifacts/comments from completed callbacks, accept agent results, and project agent events into activity/audit streams. Provider credentials and callback private keys are stored as encrypted database secrets through a cipher abstraction that can later move to Vault/KMS/Secrets Manager. Callback key rotation now immediately invalidates older callback JWTs. Generic-worker protocol endpoints now support polling claims, durable outbound webhook dispatch through configured domain-event consumers, provider-level max-attempt/dead-letter settings, per-worker token auth via `worker_token` credential `metadata.workerId`, optional worker-token expiry, heartbeat, logs, messages, artifacts, cancel, retry, and callback handoff. Human messages and request-changes flows are wired for agent tasks. Assignment and callback-authored comments verify the agent user's project permissions. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution, and adapter contract tests cover dispatch, retry, cancellation, and generic-worker protocol payload shape. Accepting an agent result can move a work item to Approval when a direct workflow transition exists and the accepting user has transition permission.

Local runtime polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed.

## Primary Next Steps

### 1. Operational Hardening

Goal: tighten production-facing backend behavior before a broad manual test.

Planned work:

- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add reporting snapshot backfill/reconciliation controls and retention policy if snapshot volume becomes meaningful.

Done when:

- The main backend flows can be exercised manually with fewer hidden operational gaps.

### 2. Reporting And Dashboard Hardening

Goal: round out dashboard/reporting APIs now that saved dashboards and cross-project summaries exist.

Planned work:

- Add read APIs for persisted cycle-time, cumulative-flow, velocity, and iteration snapshot records.
- Add saved filter CRUD and connect saved filters to dashboard widget query config.
- Add dashboard permission tests for private, workspace-shared, team-shared, non-member, and token-scope cases.
- Add reporting snapshot backfill/reconciliation controls and retention policy if snapshot volume becomes meaningful.

Done when:

- Dashboard screens can rely on saved widget configuration and persisted snapshot reads without one-off backend queries.

### 3. Frontend API Contract Preparation

Goal: make the backend easier to consume from the Vite React frontend.

Planned work:

- Add typed API examples or generated OpenAPI coverage for setup, auth, work items, teams, planning, reporting, dashboards, and agents.
- Make list endpoints consistently pageable or explicitly documented as small configuration lists.
- Review DTO names and response shapes for frontend ergonomics before UI work starts.

Done when:

- The frontend can build against stable request/response contracts instead of reading backend code directly.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Read APIs for persisted flow, cycle time, velocity, and iteration scope-change snapshots.
- Iteration reports for committed work, completed work, carryover, velocity inputs, and scope change.
- Saved filter CRUD and saved-filter-backed dashboard widgets.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add read endpoints for persisted cycle-time, flow, velocity, and iteration snapshots.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide when to introduce configurable custom fields, screens, and screen assignments beyond the current core system field model.

### Test Readiness

- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token expiry tests, token revocation edge cases, and invitation expiry tests.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add migration tests whenever the schema changes.
- Add repository/query tests for list screens and detail pages.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add automation tests once the first automation executor exists.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Add dashboard permission tests for private, workspace-shared, team-shared, non-member, and token-scope cases.
- Add broader reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Connect the Vite React app to the backend through a typed API client.
- Add a local environment configuration for backend base URL.
- Build the full initial frontend flow for setup, project navigation, backlog management, board interaction, and issue detail editing.
- Add browser CSRF token handling for cookie-authenticated unsafe requests.
- Add API token administration screens for personal and service tokens.
- Add collaboration panels for comments, links, watchers, work logs, labels, and attachments.
- Add dashboard screens that consume reporting APIs and saved widget configuration.
- Add agent-aware assignee display, including agent badges without changing the normal assignee workflow.
- Add an agent task panel on work item detail for status, progress events, questions, artifacts, branches, and pull requests.
- Add admin screens for provider configuration, agent profiles, credentials, capabilities, repository connections, and concurrency limits.
- Add frontend test coverage for setup, project navigation, work item editing, collaboration panels, reporting dashboards, and token administration.

## Execution Notes

- `TECH_SPECS.MD` remains the source of truth for the intended product architecture, schema, and domain model.
- This file tracks execution order, open implementation questions, and readiness work.
- Trasck is not being built as a reduced prototype. Work can still be sequenced, but each slice should fit the full product direction already documented in the tech spec.
- AI agent support should remain optional and provider-neutral. Normal project management workflows must keep working when no agent providers are configured.
- After each task, update this file with completed work, newly discovered questions, and the next highest-value implementation step.
