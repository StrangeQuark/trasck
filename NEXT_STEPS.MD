# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should the next agent adapter pass add a provider-specific adapter first, such as Codex, Claude Code, or webhook workers, or harden an allowlisted local runner first?
    - Answer:
2. Should agent callback JWT signing stay as a shared HS256 application secret for now, or move to per-provider asymmetric keys/JWKS before any real external provider is enabled?
    - Answer:
3. Should accepting an agent result only mark the agent task complete, or should it also queue/perform a workflow transition toward Approval once workflow automation exists?
    - Answer:
4. Should agent profiles remain workspace-level with repository/task validation, or should Trasck add project-scoped agent availability and capability rules before provider-specific adapters?
    - Answer:

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired, default local storage is seeded from configuration, comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, and these APIs publish durable domain events with HTTP integration coverage.

Activity and audit foundations are implemented. Static durable consumers project domain events into work-item/project/workspace activity streams and workspace audit logs. Audit retention policy metadata is configurable per workspace. Admin replay can reset and dispatch selected durable consumers, defaulting to activity and audit projection. Activity/audit projection tests cover visibility, redaction, and idempotent replay.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

The first provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated agent providers, rotate provider credentials without exposing raw secrets, create assignable agent profiles backed by agent users, add generic Git/GitHub/GitLab repository connections, assign work items to agents, receive signed JWT callbacks, create review artifacts/comments from completed callbacks, accept agent results, and project agent events into activity/audit streams. Assignment and callback-authored comments verify the agent user's project permissions. The simulated adapter proves queued-to-running lifecycle behavior without shell commands or paid/external AI execution.

## Primary Next Steps

### 1. Add Work Logs And Reporting History Foundations

Goal: complete the remaining collaboration/time-tracking slice and start producing the history needed for useful reports.

Planned work:

- Add work log create/update/delete/list APIs with permission checks and rich description payloads.
- Publish durable domain events for work log changes.
- Add first reporting consumers for work item status/assignment/estimate history where those are not already covered.
- Add tests for time tracking permissions, soft delete behavior, activity projection, and reportable history.

Done when:

- Users can track time on work items.
- Time tracking changes are visible in activity and safe for later reporting.
- Reporting history has a clear source of truth for the first dashboard/list views.

### 2. Add API Documentation And Local Runtime Polish

Goal: make the backend ready for a meaningful manual pass before frontend work depends on it heavily.

Planned work:

- Add OpenAPI documentation or checked-in HTTP examples for setup, auth, tokens, work items, collaboration, and attachments.
- Add Docker Compose/local profile guidance for predictable backend plus PostgreSQL startup.
- Clean up remaining default warnings where practical, including open-in-view configuration and duplicate `org.json.JSONObject` dependencies.
- Add endpoint examples that show cookie CSRF and Bearer/API-token usage separately.

Done when:

- A developer can start the backend locally, run the core flows, and understand the required auth headers/cookies without reading implementation code.

### 3. Harden And Expand Agent Adapter Contracts

Goal: prepare the provider-neutral agent foundation for real adapters without weakening normal project-management behavior.

Planned work:

- Decide whether the next adapter is provider-specific, webhook/polling, or a hardened allowlisted local runner.
- Add adapter contract tests for dispatch, retry, cancel, callback validation, failure handling, and artifact mapping.
- Add callback signing hardening if external providers are next, including per-provider keys or asymmetric assertions if selected.
- Add request-changes/waiting-for-input flows so humans can interact with an agent task without accepting or canceling it.
- Add explicit tests proving agent users cannot exceed their workspace/project role permissions.

Done when:

- A real provider can be added by implementing an adapter and tests without changing work item, workflow, or reporting code.
- Callback trust, secret handling, and agent permissions are strong enough for a non-simulated provider.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add provider-specific agent adapters or a hardened allowlisted local runner after the simulated adapter contract is stable.
- Replace simulated credential fingerprinting with decryptable encrypted secret storage or external secret management before real provider API dispatch.
- Harden signed agent callbacks for external providers with the chosen per-provider/shared/asymmetric key strategy.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, and external callback failures as those flows are added.
- Add physical audit retention pruning/export jobs after the retention policy metadata has a scheduled executor.
- Add token expiry integration tests and any remaining token lifecycle edge cases.
- Add work log APIs and reporting history consumers for time tracking.
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
- Add secret-redaction tests proving provider credentials do not leak into context snapshots, comments, activity events, or audit entries.
- Add reporting snapshot tests after work item updates begin writing reportable state.

### Frontend Readiness

- Connect the Vite React app to the backend through a typed API client.
- Add a local environment configuration for backend base URL.
- Build the full initial frontend flow for setup, project navigation, backlog management, board interaction, and issue detail editing.
- Add browser CSRF token handling for cookie-authenticated unsafe requests.
- Add API token administration screens for personal and service tokens.
- Add collaboration panels for comments, links, watchers, labels, and attachments.
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
