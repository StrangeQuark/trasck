# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. For real Codex and Claude Code dispatch, should Trasck use a backend-managed CLI worker, hosted provider APIs/webhooks, or a generic worker protocol first?
    - Answer:
2. Should provider callback private keys remain generated in provider config for development, move to encrypted database storage, or move directly to external KMS/secret management before any non-stub adapter is enabled?
    - Answer:
3. Should reporting/history get dedicated REST endpoints now, or wait until dashboard/report APIs are designed?
    - Answer:
4. Should work log edit/delete permissions stay `work_item.update` for all mutations, or move to author-or-admin/self-time-entry rules?
    - Answer:

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired, default local storage is seeded from configuration, comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, work logs soft-delete and publish durable activity-ready events, and these APIs publish durable domain events with HTTP integration coverage.

Initial reporting history writes are in place for work item status, assignment, and estimate changes. Snapshot jobs and report/dashboard read APIs remain future work.

Activity and audit foundations are implemented. Static durable consumers project domain events into work-item/project/workspace activity streams and workspace audit logs. Audit retention policy metadata is configurable per workspace. Admin replay can reset and dispatch selected durable consumers, defaulting to activity and audit projection. Activity/audit projection tests cover visibility, redaction, and idempotent replay.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

The first provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, and Claude Code agent providers, rotate provider credentials without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add generic Git/GitHub/GitLab repository connections, assign work items to agents, receive per-provider RS256/JWKS-style signed callbacks, create review artifacts/comments from completed callbacks, accept agent results, and project agent events into activity/audit streams. Assignment and callback-authored comments verify the agent user's project permissions. The current adapters prove queued-to-running lifecycle behavior without shell commands or paid/external AI execution. Accepting an agent result can move a work item to Approval when a direct workflow transition exists and the accepting user has transition permission.

## Primary Next Steps

### 1. Add API Documentation And Local Runtime Polish

Goal: make the backend ready for a meaningful manual pass before frontend work depends on it heavily.

Planned work:

- Add OpenAPI documentation or checked-in HTTP examples for setup, auth, tokens, work items, collaboration, attachments, work logs, agent providers, agent profiles, repository connections, and agent tasks.
- Add Docker Compose/local profile guidance for predictable backend plus PostgreSQL startup.
- Clean up remaining default warnings where practical, including open-in-view configuration and duplicate `org.json.JSONObject` dependencies.
- Add endpoint examples that show cookie CSRF and Bearer/API-token usage separately.

Done when:

- A developer can start the backend locally, run the core flows, and understand the required auth headers/cookies without reading implementation code.

### 2. Harden Real Agent Adapter Contracts

Goal: prepare the provider-neutral agent foundation for real Codex/Claude Code or worker-based execution without weakening normal project-management behavior.

Planned work:

- Decide whether real dispatch starts with backend-managed CLI workers, hosted provider APIs/webhooks, or a generic worker protocol.
- Add adapter contract tests for dispatch, retry, cancel, callback validation, failure handling, and artifact mapping.
- Move callback signing private keys and provider credentials out of plain provider config/fingerprints before external dispatch.
- Add request-changes/waiting-for-input flows so humans can interact with an agent task without accepting or canceling it.
- Add explicit tests proving agent users cannot exceed their workspace/project role permissions.

Done when:

- A real provider can dispatch through the adapter contract without changing work item, workflow, or reporting code.
- Callback trust, secret handling, and agent permissions are strong enough for non-stub provider execution.

### 3. Add Reporting Read APIs And Snapshots

Goal: turn the stored status, assignment, estimate, and work-log data into useful report surfaces.

Planned work:

- Add read APIs for work item status history, assignment history, estimate history, and work-log totals.
- Add the first report endpoints for project/team throughput, cycle time inputs, and estimate/time summaries.
- Decide which snapshots are calculated on demand first versus persisted by scheduled jobs.
- Add tests for report permission checks, date ranges, deleted work logs, and cross-project ordering.

Done when:

- The backend can answer basic reporting questions without frontend code reading raw tables directly.
- The reporting model has a clear path from history rows to dashboard widgets.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add real dispatch for Codex/Claude Code, a generic worker protocol, or a hardened allowlisted local runner after the non-external adapter contract is stable.
- Replace simulated credential fingerprinting and provider-config private key storage with decryptable encrypted secret storage or external secret management before real provider API dispatch.
- Add key rotation and operational controls for per-provider callback signing keys.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add reporting read APIs and snapshot jobs for status, assignment, estimate, and time tracking history.
- Add OpenAPI documentation or HTTP example files for the main workflows.
- Add Docker Compose/local profile support for running the backend with PostgreSQL in a predictable local setup.
- Clean up remaining default warnings where practical, including open-in-view configuration and duplicate `org.json.JSONObject` dependencies.

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
