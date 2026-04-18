# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should OAuth provider email verification stay generic for now, or should production OAuth add provider-specific verified-email fetching before non-Google providers can auto-link by email?
2. Should personal/service token `scopes` remain stored metadata for now, or should the next auth pass enforce scopes in addition to role permissions?
3. Should service-token-backed `service` users appear in normal user/member lists, or should they be hidden behind dedicated service-token/admin screens by default?
4. Should the first durable event consumers be static Spring beans, or should webhook/notification/automation consumers be configured from database records and resolved dynamically?

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, method-level admin permissions, explicit service-level work item permission checks, and durable per-consumer outbox delivery are in place.

The first work item API slice is implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. The service validates parent-child type rules, project type enablement, same-project structural parentage, workflow assignment/status ownership, transition validity, and existing child validity during type changes. Work item changes maintain closure rows, status history, assignment history, sortable rank strings, project-local sequences, workspace-wide sequences, durable `domain_events` rows, and in-process Spring events.

Core JPA mappings now include useful associations for common navigation paths such as workspace/project, project/work item, work item/type/status/priority, and workflow/status/transition while preserving scalar UUID fields where they keep command code explicit.

## Primary Next Steps

### 1. Add Comment, Link, Watcher, Label, And Basic Attachment APIs

Goal: complete the core work item collaboration surface before richer planning and AI workflows are layered on.

Planned work:

- Add comment create/update/delete/list APIs.
- Add work item link create/delete/list APIs.
- Add watcher add/remove/list APIs.
- Add label create/list and work item label assignment APIs.
- Add basic attachment metadata APIs, using the storage abstraction from the attachment task when file bytes are introduced.
- Publish domain events for collaboration changes.
- Add integration tests for comments, links, watchers, labels, and attachment metadata.

Done when:

- A user-facing work item detail page can show and edit the primary collaboration data.
- Collaboration changes are represented in durable domain events.
- Tests cover valid and invalid link/comment/watch/label flows.

### 2. Finish Auth Production Edges And Token Scope Enforcement

Goal: close the remaining auth details now that OAuth, CSRF, personal tokens, and service tokens exist.

Planned work:

- Decide and implement provider-specific verified-email handling for GitHub, GitLab, and Microsoft OAuth.
- Add list/detail APIs for personal tokens and service tokens without exposing token hashes or raw token values.
- Decide whether token scopes are metadata or enforced authorization constraints, then implement the chosen behavior.
- Add token expiry and revocation tests around both JWT-style access and API-token access.
- Review cookie security defaults for local development versus production deployment.

Done when:

- OAuth auto-linking policy is explicit and test-covered for the supported providers.
- API tokens have a complete lifecycle: create, list, use, expire, revoke.
- Token scopes, if enforced, cannot bypass role permissions or grant unexpected access.

### 3. Add Configurable Attachment Storage Service

Goal: turn the seeded attachment storage configuration into a working storage abstraction before work item attachments expose file bytes.

Planned work:

- Add an attachment storage service interface.
- Implement filesystem storage as the default local provider.
- Resolve active/default storage configuration per workspace.
- Add attachment upload/download metadata flows for work items.
- Add tests for storage selection, local writes, cleanup, and error handling.

Done when:

- Work item attachments can be stored through a provider-neutral interface.
- Local development uses filesystem storage by default.
- Future S3-compatible providers can be added without changing work item logic.

### 4. Add Provider-Neutral AI Agent Service Foundations

Goal: let work items be assigned to AI agents without making Trasck dependent on any one agent vendor or changing the normal Jira/Rally-style workflow.

Planned work:

- Add an `AgentProviderAdapter` service contract that supports configuration validation, capability discovery, dispatch, cancel, retry, and callback handling.
- Add provider, credential, agent profile, repository connection, and agent task APIs on top of the existing schema/JPA foundation.
- Add Codex as the first real provider adapter, with tests using controlled stubs where external execution should not run.
- Keep provider-specific behavior inside adapters selected by `provider_type`.
- Add audit and activity events for agent assignment, provider configuration, credential rotation, task status changes, messages, and artifacts.
- Add tests for assignment, dispatch, callback idempotency, cancellation, retry, completion, failure, artifact creation, and permission enforcement.

Done when:

- A work item can be assigned to a configured Codex agent through the same assignment model used for humans.
- Agent execution state is visible and auditable without closing the work item automatically.
- A future provider such as Claude Code, a local runner, or a webhook worker can be added by implementing the adapter contract rather than changing work item or workflow logic.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add comment, link, watcher, label, and basic attachment APIs.
- Add list/revoke/expiry polish and scope enforcement decisions for personal and service tokens.
- Add provider-specific OAuth email verification hardening for production use.
- Add provider-neutral agent provider, agent profile, repository connection, and agent task APIs.
- Add Codex as the first real agent provider adapter while keeping tests isolated from paid or external AI execution.
- Add authenticated, idempotent agent callback endpoints.
- Implement configurable attachment storage behind a stable interface, defaulting local development to filesystem storage.
- Add audit writes for security-sensitive and work-item-changing operations.
- Add audit writes for agent provider setup, credential rotation, agent assignment, privileged task actions, and callback failures.
- Add OpenAPI documentation or HTTP example files for the main workflows.
- Add Docker Compose support for running the backend with PostgreSQL in a predictable local setup.
- Clean up remaining default warnings where practical, including open-in-view configuration and duplicate `org.json.JSONObject` dependencies.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Decide when to introduce configurable custom fields, screens, and screen assignments beyond the current core system field model.

### Test Readiness

- Expand permission tests for hierarchy, workflow, work item APIs, user management, future collaboration APIs, and public read access.
- Add provider-specific OAuth callback tests, token expiry tests, token revocation edge cases, and invitation expiry tests.
- Add durable outbox consumer tests for failure, retry, and multi-consumer delivery.
- Add migration tests whenever the schema changes.
- Add repository/query tests for list screens and detail pages.
- Add attachment storage tests with the first local provider.
- Add automation tests once the first automation executor exists.
- Add Codex adapter contract tests covering dispatch, callback, messages, artifacts, cancellation, retry, failure, completion, and idempotency without invoking paid or external execution.
- Add agent permission tests proving agents cannot exceed their workspace/project role.
- Add secret-redaction tests proving provider credentials do not leak into context snapshots, comments, or activity events.
- Add reporting snapshot tests after work item updates begin writing reportable state.

### Frontend Readiness

- Connect the Vite React app to the backend through a typed API client.
- Add a local environment configuration for backend base URL.
- Build the full initial frontend flow for setup, project navigation, backlog management, board interaction, and issue detail editing.
- Add browser CSRF token handling for cookie-authenticated unsafe requests.
- Add API token administration screens for personal and service tokens.
- Add agent-aware assignee display, including agent badges without changing the normal assignee workflow.
- Add an agent task panel on work item detail for status, progress events, questions, artifacts, branches, and pull requests.
- Add admin screens for provider configuration, agent profiles, credentials, capabilities, repository connections, and concurrency limits.
- Add frontend test coverage for setup, project navigation, and work item editing.

## Execution Notes

- `TECH_SPECS.MD` remains the source of truth for the intended product architecture, schema, and domain model.
- This file tracks execution order, open implementation questions, and readiness work.
- Trasck is not being built as a reduced prototype. Work can still be sequenced, but each slice should fit the full product direction already documented in the tech spec.
- AI agent support should remain optional and provider-neutral. Normal project management workflows must keep working when no agent providers are configured.
- After each task, update this file with completed work, newly discovered questions, and the next highest-value implementation step.
