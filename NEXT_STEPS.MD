# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should saved dashboards initially be user-owned only, or should workspace/team-shared dashboards be supported in the first dashboard CRUD pass?
    - Answer:
2. Should dashboard widgets start with a strict allowlist of supported widget types, or allow a more flexible query-backed widget model immediately?
    - Answer:
3. Should workspace/program cross-project summaries group by project first, team first, work item type first, or expose all three dimensions in the first response shape?
    - Answer:
4. Should full team/planning APIs start with team CRUD, memberships, and project-team assignment, or start with iteration planning and sprint commitment management?
    - Answer:
5. Should outbound worker webhook delivery add provider-level max-attempt/dead-letter settings before any non-test external worker is used?
    - Answer:

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired. Work items now support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project. Dedicated team assignment command, history, and events are implemented through `POST /api/v1/work-items/{workItemId}/team`, `work_item_team_history`, `work_item.team_changed`, and `GET /api/v1/reports/work-items/{workItemId}/team-history`. Comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, work-log self-service uses dedicated own-entry permissions, and these APIs publish durable domain events with HTTP integration coverage.

Reporting foundations now include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. The first on-demand dashboard summary API is implemented at `GET /api/v1/reports/projects/{projectId}/dashboard-summary`. It supports exact UTC `from`/`to` filters, project/team/iteration scopes, explicit team assignment with membership-snapshot fallback, non-deleted work-log totals, work counts, throughput, estimate/time totals, aging WIP, cycle-time inputs, status/type/priority groupings, and widget-shaped response data. Persisted snapshot jobs now populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with a manual trigger at `POST /api/v1/reports/workspaces/{workspaceId}/snapshots/run`; dashboard cycle-time output uses `cycle_time_records` when available. Cross-project/program reporting and saved dashboard CRUD remain future work.

Activity and audit foundations are implemented. Static durable consumers project domain events into work-item/project/workspace activity streams and workspace audit logs. Audit retention policy metadata is configurable per workspace. Admin replay can reset and dispatch selected durable consumers, defaulting to activity and audit projection. Activity/audit projection tests cover visibility, redaction, and idempotent replay.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add generic Git/GitHub/GitLab repository connections, assign work items to agents, receive per-provider RS256/JWKS-style signed callbacks, create review artifacts/comments from completed callbacks, accept agent results, and project agent events into activity/audit streams. Provider credentials and callback private keys are stored as encrypted database secrets through a cipher abstraction that can later move to Vault/KMS/Secrets Manager. Callback key rotation now immediately invalidates older callback JWTs. Generic-worker protocol endpoints now support polling claims, durable outbound webhook dispatch through configured domain-event consumers, per-worker token auth via `worker_token` credential `metadata.workerId`, optional worker-token expiry, heartbeat, logs, messages, artifacts, cancel, retry, and callback handoff. Human messages and request-changes flows are wired for agent tasks. Assignment and callback-authored comments verify the agent user's project permissions. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution, and adapter contract tests cover dispatch, retry, cancellation, and generic-worker protocol payload shape. Accepting an agent result can move a work item to Approval when a direct workflow transition exists and the accepting user has transition permission.

Local runtime polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed.

## Primary Next Steps

### 1. Build Saved Dashboards And Cross-Project Reporting

Goal: turn the first dashboard summary API into reusable dashboard surfaces and broader portfolio views.

Planned work:

- Add dashboard and dashboard-widget CRUD APIs with permission checks and widget config validation.
- Connect saved widgets to reporting API output instead of raw domain tables.
- Add workspace/program/project-set dashboard summaries ordered by `workspace_sequence_number` for cross-project reporting.
- Use existing persisted snapshots for cycle-time, velocity, iteration scope, and cumulative-flow widgets where those snapshots are a better fit than live queries.

Done when:

- Users can save dashboards whose widgets read reporting APIs.
- Workspace/program dashboards can compare work across projects without each frontend screen stitching project reports manually.

### 2. Implement Full Team And Planning APIs

Goal: expose the team and iteration management surfaces that reporting and work item assignment now assume.

Planned work:

- Add team CRUD, team membership, and project-team assignment APIs with workspace/project permission checks.
- Add iteration CRUD, iteration work item add/remove, sprint commitment, and close/carryover APIs.
- Make team assignment history and iteration membership changes visible in activity/reporting where they affect planning.
- Add tests for explicit team assignment, membership-snapshot fallback, iteration scope change, and permission boundaries.

Done when:

- Users can configure teams and iterations through APIs instead of direct seed/test data.
- Reporting scopes can rely on first-class team/planning commands and histories.

### 3. Operational Hardening

Goal: tighten production-facing backend behavior before a broad manual test.

Planned work:

- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add worker webhook dead-letter/max-attempt policy once the provider-level behavior is decided.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add reporting snapshot backfill/reconciliation controls and retention policy if snapshot volume becomes meaningful.

Done when:

- The main backend flows can be exercised manually with fewer hidden operational gaps.

## Remaining Dashboard/Report APIs

- Workspace/program dashboard summaries for cross-project reporting.
- Saved dashboard and dashboard-widget CRUD APIs.
- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Read APIs for persisted flow, cycle time, velocity, and iteration scope-change snapshots.
- Iteration reports for committed work, completed work, carryover, velocity inputs, and scope change.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add workspace/program dashboard aggregate APIs and read endpoints for persisted cycle-time, flow, velocity, and iteration snapshots.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide when to introduce configurable custom fields, screens, and screen assignments beyond the current core system field model.

### Test Readiness

- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, public read access, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token expiry tests, token revocation edge cases, and invitation expiry tests.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add migration tests whenever the schema changes.
- Add repository/query tests for list screens and detail pages.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add automation tests once the first automation executor exists.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
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
