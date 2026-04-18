# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should the first activity projection be work-item scoped only, or should project/workspace activity streams be created in the same pass?
    - Answer:
2. Should audit logs use permanent retention by default, or should retention/export policy be configurable per workspace from the first audit pass?
    - Answer:
3. Should durable activity/audit consumers get an admin replay endpoint in this pass, or should they rely on the scheduled dispatcher plus database delivery state until admin APIs exist?
    - Answer:
4. Should the first stub/local AI runner execute an allowlisted local command, or should it only simulate the provider-neutral lifecycle without running shell commands?
    - Answer:

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-level work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. Work item detail data now includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired, default local storage is seeded from configuration, comment edit/delete uses the author-or-admin permission shape, watcher self-service works with read permission, and these APIs publish durable domain events with HTTP integration coverage.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

## Primary Next Steps

### 1. Add Activity, Audit, And Durable Consumer Wiring

Goal: make user-visible activity and security/compliance audit data real consumers of the domain changes already being written.

Planned work:

- Implement durable consumers that project `domain_events` into `activity_events` and `audit_log_entries`.
- Add consumers for work item create/update/archive, collaboration changes, attachment changes, auth/user-management changes, and token changes.
- Add redaction helpers so secrets and raw token values never appear in activity, audit, comments, or context snapshots.
- Add tests for activity visibility, audit coverage, durable consumer retry/failure behavior, and idempotency.

Done when:

- Important product changes appear in activity data.
- Security-sensitive changes have audit rows.
- Durable consumer delivery records prove each configured consumer completed or failed clearly.

### 2. Add Provider-Neutral AI Agent Service Foundations

Goal: let work items be assigned to AI agents without making Trasck dependent on any one agent vendor or changing the normal Jira/Rally-style workflow.

Planned work:

- Add an `AgentProviderAdapter` service contract that supports configuration validation, capability discovery, dispatch, cancel, retry, and callback handling.
- Add provider, credential, agent profile, repository connection, and agent task APIs on top of the existing schema/JPA foundation.
- Implement the first stub/local adapter shape from the questions above.
- Keep provider-specific behavior inside adapters selected by `provider_type`.
- Add audit and activity events for agent assignment, provider configuration, credential rotation, task status changes, messages, and artifacts.
- Add tests for assignment, dispatch, callback idempotency, cancellation, retry, completion, failure, artifact creation, and permission enforcement.

Done when:

- A work item can be assigned to a configured agent through the same assignment model used for humans.
- Agent execution state is visible and auditable without closing the work item automatically.
- A future provider such as Codex, Claude Code, a local runner, or a webhook worker can be added by implementing the adapter contract rather than changing work item or workflow logic.

### 3. Add API Documentation And Local Runtime Polish

Goal: make the backend ready for a meaningful manual pass before frontend work depends on it heavily.

Planned work:

- Add OpenAPI documentation or checked-in HTTP examples for setup, auth, tokens, work items, collaboration, and attachments.
- Add Docker Compose/local profile guidance for predictable backend plus PostgreSQL startup.
- Clean up remaining default warnings where practical, including open-in-view configuration and duplicate `org.json.JSONObject` dependencies.
- Add endpoint examples that show cookie CSRF and Bearer/API-token usage separately.

Done when:

- A developer can start the backend locally, run the core flows, and understand the required auth headers/cookies without reading implementation code.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Implement activity projections for work-item-changing operations.
- Implement audit projections for security-sensitive operations, token lifecycle changes, provider setup, credential rotation, agent assignment, privileged task actions, and callback failures.
- Add provider-neutral agent provider, agent profile, repository connection, and agent task APIs.
- Add the first stub/local agent adapter while keeping tests isolated from paid or external AI execution.
- Add authenticated, idempotent agent callback endpoints.
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
- Add durable outbox consumer tests for failure, retry, disabled consumers, and multi-consumer delivery.
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
