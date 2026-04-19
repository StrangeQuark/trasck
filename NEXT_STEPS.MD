# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should the next reporting pass prioritize saved dashboard/widget CRUD, or workspace/program cross-project dashboard summaries?
    - Answer: Implement both. Prioritize saved dashboard/widget CRUD first so the frontend has a stable configurable dashboard surface, then add workspace/program cross-project summaries as reporting data sources for those widgets.
2. Should explicit work-item team assignment changes get dedicated history/events and a command endpoint, or stay as part of normal work item update until team/planning APIs are added?
    - Answer: Give them dedicated history/events and a command endpoint. And go ahead and add a new section in Primary Next Steps to implement the full team/planning APIs
3. Should cycle-time reporting switch now to status-derived `cycle_time_records`, or keep the current created-to-completed lead-time input until snapshot jobs are implemented?
    - Answer: Just implement snapshot jobs now
4. For real generic-worker dispatch, should outbound webhook delivery be implemented through a new agent delivery job table first, or through durable domain events with a configured consumer?
    - Answer: Durable domain events

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired. Work items now support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project. Comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, work-log self-service uses dedicated own-entry permissions, and these APIs publish durable domain events with HTTP integration coverage.

Reporting foundations now include status, assignment, and estimate history writes plus dedicated read APIs for work item status history, assignment history, estimate history, and work-log summaries. The first on-demand dashboard summary API is implemented at `GET /api/v1/reports/projects/{projectId}/dashboard-summary`. It supports exact UTC `from`/`to` filters, project/team/iteration scopes, explicit team assignment with membership-snapshot fallback, non-deleted work-log totals, work counts, throughput, estimate/time totals, aging WIP, lead-time inputs, status/type/priority groupings, and widget-shaped response data. Cross-project/program reporting, saved dashboard CRUD, and persisted snapshot jobs remain future work.

Activity and audit foundations are implemented. Static durable consumers project domain events into work-item/project/workspace activity streams and workspace audit logs. Audit retention policy metadata is configurable per workspace. Admin replay can reset and dispatch selected durable consumers, defaulting to activity and audit projection. Activity/audit projection tests cover visibility, redaction, and idempotent replay.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add generic Git/GitHub/GitLab repository connections, assign work items to agents, receive per-provider RS256/JWKS-style signed callbacks, create review artifacts/comments from completed callbacks, accept agent results, and project agent events into activity/audit streams. Provider credentials and callback private keys are stored as encrypted database secrets through a cipher abstraction that can later move to Vault/KMS/Secrets Manager. Callback key rotation now immediately invalidates older callback JWTs. Generic-worker protocol endpoints now support polling claims, backend-generated webhook dispatch payloads, per-worker token auth via `worker_token` credential `metadata.workerId`, heartbeat, logs, messages, artifacts, cancel, retry, and callback handoff. Human messages and request-changes flows are wired for agent tasks. Assignment and callback-authored comments verify the agent user's project permissions. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution. Accepting an agent result can move a work item to Approval when a direct workflow transition exists and the accepting user has transition permission.

Local runtime polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed.

## Primary Next Steps

### 1. Harden Real Agent Adapter Execution

Goal: use the generic worker foundation for real Codex, Claude Code, self-hosted, or future agents without weakening normal project-management behavior.

Planned work:

- Add adapter contract tests for dispatch, retry, cancel, callback validation, failure handling, artifact mapping, and idempotency.
- Add the first outbound worker delivery path for providers configured with `callbackUrl`, backed by durable jobs/events rather than synchronous external calls.
- Add worker-token expiry, rotation, and revocation edge-case coverage for per-worker credentials.
- Keep Codex and Claude Code provider code behind the same provider-neutral contract instead of special-casing work item, workflow, or reporting services.

Done when:

- A real provider or worker can execute through the adapter contract without changing work item, workflow, or reporting code.
- Callback trust, secret handling, worker identity, and agent permissions are strong enough for non-stub provider execution.

### 2. Build Saved Dashboards And Cross-Project Reporting

Goal: turn the first dashboard summary API into reusable dashboard surfaces and broader portfolio views.

Planned work:

- Add dashboard and dashboard-widget CRUD APIs with permission checks and widget config validation.
- Connect saved widgets to reporting API output instead of raw domain tables.
- Add workspace/program/project-set dashboard summaries ordered by `workspace_sequence_number` for cross-project reporting.
- Decide which summary metrics stay on demand and which get persisted scheduled snapshots.

Done when:

- Users can save dashboards whose widgets read reporting APIs.
- Workspace/program dashboards can compare work across projects without each frontend screen stitching project reports manually.

### 3. Operational Hardening

Goal: tighten production-facing backend behavior before a broad manual test.

Planned work:

- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add reporting snapshot jobs for cumulative flow, velocity, iteration scope change, and cycle-time records.

Done when:

- The main backend flows can be exercised manually with fewer hidden operational gaps.

## Remaining Dashboard/Report APIs

- Workspace/program dashboard summaries for cross-project reporting.
- Saved dashboard and dashboard-widget CRUD APIs.
- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Flow reporting backed by snapshots: cumulative flow, cycle time, lead time, throughput, aging WIP, and stale work items.
- Iteration reports for committed work, completed work, carryover, velocity inputs, and scope change.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add the first real dispatch path through the generic worker protocol, provider-hosted APIs, or hardened CLI workers after adapter contract tests are stable.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add workspace/program dashboard aggregate APIs and persisted snapshot jobs for status, assignment, estimate, cycle time, flow, and time tracking history.

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
- Expand agent adapter contract tests covering dispatch, callback, messages, artifacts, cancellation, retry, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Add reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history.

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
