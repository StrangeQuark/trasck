# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. For the generic worker protocol, should the first real worker transport be pull-based polling, backend push/webhook delivery, or both behind the same protocol?
    - Answer:
2. Should encrypted provider secret rotation get admin re-encrypt/rotate APIs before external providers are enabled, or wait until the first real adapter needs them?
    - Answer:
3. For dashboard/report APIs, should the first aggregate surface be project-level, team/iteration-level, or both using the same query model?
    - Answer:
4. Should self-service time tracking get dedicated permissions such as `work_log.create_own` and `work_log.update_own` before custom role-management screens exist?
    - Answer:

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired, default local storage is seeded from configuration, comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, work-log self-service works for a user's own time entries with read permission, and these APIs publish durable domain events with HTTP integration coverage.

Reporting foundations now include status, assignment, and estimate history writes plus dedicated read APIs for work item status history, assignment history, estimate history, and work-log summaries. These endpoints require `report.read` on the work item's project. Dashboard/report aggregate APIs, date-range filtering, cross-project reporting, and snapshot jobs remain future work.

Activity and audit foundations are implemented. Static durable consumers project domain events into work-item/project/workspace activity streams and workspace audit logs. Audit retention policy metadata is configurable per workspace. Admin replay can reset and dispatch selected durable consumers, defaulting to activity and audit projection. Activity/audit projection tests cover visibility, redaction, and idempotent replay.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, rotate provider credentials without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add generic Git/GitHub/GitLab repository connections, assign work items to agents, receive per-provider RS256/JWKS-style signed callbacks, create review artifacts/comments from completed callbacks, accept agent results, and project agent events into activity/audit streams. Provider credentials and callback private keys are stored as encrypted database secrets through a cipher abstraction that can later move to Vault/KMS/Secrets Manager. Assignment and callback-authored comments verify the agent user's project permissions. Current adapters prove queued-to-running lifecycle behavior without shell commands or paid/external AI execution. Accepting an agent result can move a work item to Approval when a direct workflow transition exists and the accepting user has transition permission.

Local runtime polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed.

## Primary Next Steps

### 1. Harden Generic Worker Protocol And Agent Adapter Contracts

Goal: prepare the provider-neutral agent foundation for real Codex, Claude Code, self-hosted, or future agents without weakening normal project-management behavior.

Planned work:

- Decide the first real worker transport shape from the questions above.
- Define worker protocol endpoints and payloads for task claim/dispatch, heartbeat, cancel, retry, callback, logs, messages, and artifacts.
- Add adapter contract tests for dispatch, retry, cancel, callback validation, failure handling, artifact mapping, and idempotency.
- Add request-changes and human waiting-for-input flows so people can interact with an agent task without accepting or canceling it.
- Add explicit tests proving agent users cannot exceed their workspace/project role permissions.

Done when:

- A real provider or worker can dispatch through the adapter contract without changing work item, workflow, or reporting code.
- Callback trust, secret handling, and agent permissions are strong enough for non-stub provider execution.

### 2. Add Dashboard And Reporting APIs

Goal: turn stored history and work-log data into useful project-management report surfaces.

Planned work:

- Add aggregate APIs for project/team throughput, cycle time inputs, estimate/time summaries, and work aging.
- Add date ranges, grouping controls, deleted-work-log behavior, and cross-project ordering.
- Decide which metrics calculate on demand first versus which need persisted scheduled snapshots.
- Connect report outputs to the existing dashboard/widget schema instead of requiring the frontend to read raw history endpoints.

Done when:

- The backend can answer basic dashboard questions without frontend code reading raw tables directly.
- The reporting model has a clear path from history rows to dashboard widgets.

### 3. Operational Hardening

Goal: tighten production-facing backend behavior before a broad manual test.

Planned work:

- Add key/secret rotation controls for provider credentials and callback signing keys.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.

Done when:

- The main backend flows can be exercised manually with fewer hidden operational gaps.

## Future Dashboard/Report APIs

Keep this section until dashboard work is implemented.

- Project dashboard summary: open work, blocked work, recent transitions, work by type/status/priority, and active agents.
- Team/iteration dashboard summary: committed work, completed work, carryover, velocity inputs, and cycle-time inputs.
- Time and estimate reporting: work-log totals by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Flow reporting: cumulative flow, cycle time, lead time, throughput, aging WIP, and stale work items.
- Saved dashboard widgets should read from reporting APIs, not raw domain tables.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add real dispatch through the generic worker protocol, provider-hosted APIs, or hardened CLI workers after the non-external adapter contract is stable.
- Add key rotation and operational controls for per-provider callback signing keys and provider credentials.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add dashboard aggregate APIs and snapshot jobs for status, assignment, estimate, cycle time, flow, and time tracking history.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide when to introduce configurable custom fields, screens, and screen assignments beyond the current core system field model.
- Decide whether work-log self-service should remain permission-shaped through `work_item.read`/`work_item.update` or get dedicated time-tracking permissions.

### Test Readiness

- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, public read access, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token expiry tests, token revocation edge cases, and invitation expiry tests.
- Add durable outbox consumer tests for failure, retry, disabled consumers, unknown replay consumers, and multi-consumer delivery.
- Add migration tests whenever the schema changes.
- Add repository/query tests for list screens and detail pages.
- Add S3-compatible attachment storage tests using a controlled local fake, MinIO, or LocalStack.
- Add automation tests once the first automation executor exists.
- Add agent adapter contract tests covering dispatch, callback, messages, artifacts, cancellation, retry, failure, completion, and idempotency without invoking paid or external execution.
- Add agent permission tests proving agents cannot exceed their workspace/project role.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Add reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history.

### Frontend Readiness

- Connect the Vite React app to the backend through a typed API client.
- Add a local environment configuration for backend base URL.
- Build the full initial frontend flow for setup, project navigation, backlog management, board interaction, and issue detail editing.
- Add browser CSRF token handling for cookie-authenticated unsafe requests.
- Add API token administration screens for personal and service tokens.
- Add collaboration panels for comments, links, watchers, work logs, labels, and attachments.
- Add agent-aware assignee display, including agent badges without changing the normal assignee workflow.
- Add an agent task panel on work item detail for status, progress events, questions, artifacts, branches, and pull requests.
- Add admin screens for provider configuration, agent profiles, credentials, capabilities, repository connections, and concurrency limits.
- Add frontend test coverage for setup, project navigation, work item editing, collaboration panels, and token administration.

## Execution Notes

- `TECH_SPECS.MD` remains the source of truth for the intended product architecture, schema, and domain model.
- This file tracks execution order, open implementation questions, and readiness work.
- Trasck is not being built as a reduced prototype. Work can still be sequenced, but each slice should fit the full product direction already documented in the tech spec.
- AI agent support should remain optional and provider-neutral. Normal project management workflows must keep working when no agent providers are configured.
- After each task, update this file with completed work, newly discovered questions, and the next highest-value implementation step.
