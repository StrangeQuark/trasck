# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. Should cookie-authenticated browser API calls enable CSRF protection immediately, and should that use Spring's cookie CSRF token repository or a custom double-submit token?
2. Should the current signed `/api/v1/auth/oauth/login` verified-identity endpoint remain as an internal/provider-neutral callback for now, or should the next auth hardening task add full GitHub/Google/GitLab/Microsoft OAuth redirects and callbacks with Spring OAuth client registrations?
3. Should direct API access keep using short-lived login JWTs only, or should Trasck add revocable personal access tokens and service tokens for users, agents, and external integrations?
4. Should invitations remain workspace-only for now, or should invitations also be able to grant project-specific membership when accepted?
5. Should `domain_events.processing_status = published` mean "published in-process" as implemented now, or should events remain pending until each real consumer has completed delivery through per-consumer delivery records?

## Current State

The backend currently has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and persistence tests in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The existing automated verification covers schema creation and representative persistence across the generated JPA model using PostgreSQL through Testcontainers. The backend exposes a first-run-only setup API for creating an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are wired through the security configuration and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use the token as `Authorization: Bearer`. Invitation-based registration, workspace-admin direct user creation, signed verified OAuth identity linking, method-level admin permissions, explicit service-level work item permission checks, and durable outbox publishing are in place. Work item endpoints are locked down and no longer anonymously accessible.

The first work item API slice is implemented. Authenticated users with project/workspace permissions can create, read, update, list, assign, rank, transition, and archive work items through REST APIs. The service validates parent-child type rules, project type enablement, same-project structural parentage, workflow assignment/status ownership, transition validity, and existing child validity during type changes. Work item changes maintain closure rows, status history, assignment history, sortable rank strings, project-local sequences, workspace-wide sequences, durable `domain_events` outbox rows, and in-process Spring events.

## Primary Next Steps

### 1. Replace Raw UUID Foreign-Key Handling With Richer Associations Where Useful

Goal: keep the generated JPA foundation pragmatic while improving navigation and query code where associations provide real value.

Planned work:

- Review service and query needs before changing entity mappings.
- Add richer associations for high-traffic paths such as workspace to project, project to work item, work item to type, work item to status, workflow to statuses, and workflow to transitions.
- Keep scalar UUID fields where they reduce accidental loading or avoid unnecessary graph complexity.
- Add repository queries or specifications for list screens and detail views.
- Verify fetch behavior with integration tests so common calls do not create avoidable query explosions.

Done when:

- The service layer can express common product workflows clearly.
- The entity model supports useful navigation without turning every relationship into a large implicit object graph.
- Tests cover the changed mappings and representative queries.

### 2. Add Comment, Link, Watcher, Label, And Basic Attachment APIs

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

### 3. Harden Authentication, OAuth, Tokens, And Outbox Semantics

Goal: tighten the auth and event foundations before broader frontend and integration work depends on them.

Planned work:

- Add browser CSRF protection once the frontend request strategy is decided.
- Decide whether to keep the signed verified OAuth endpoint as an internal callback or add full provider-specific OAuth redirects/callbacks now.
- Decide whether short-lived JWTs are enough for direct API access or whether revocable personal/service tokens should be added.
- Decide whether invitation acceptance should optionally create project membership.
- Decide whether outbox `published` remains a single in-process status or moves to per-consumer delivery records.
- Add focused tests for the chosen CSRF, OAuth, token, invitation, and outbox behavior.

Done when:

- Browser auth has an explicit CSRF story.
- OAuth login cannot rely on caller-provided provider claims without backend verification or signed trusted assertions.
- Direct API token behavior is clear for users, agents, and integrations.
- Outbox processing semantics are clear before notifications, automation, and webhooks consume events.

### 4. Add Configurable Attachment Storage Service

Goal: turn the seeded attachment storage configuration into a working storage abstraction before work item attachments are exposed.

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

### 5. Add Provider-Neutral AI Agent Service Foundations

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

- Harden authentication for production use, including CSRF, cookie security defaults, OAuth provider callbacks or trusted callback assertions, and direct API token lifecycle.
- Expand permission checks beyond work items and user-management endpoints as more APIs are exposed.
- Add comment, link, watcher, label, and basic attachment APIs.
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
- Add CSRF, OAuth provider callback/assertion, token expiry, token revocation or rotation, and invitation expiry tests when those decisions are implemented.
- Add outbox consumer delivery tests once notifications, automation, activity, audit, or webhooks consume durable events.
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
