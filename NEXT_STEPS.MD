# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend slices.

1. For username/password plus OAuth, which OAuth providers should be enabled first: GitHub, Google, GitLab, Microsoft, or another provider?
    - Answer: Implement all 4 of these
2. Should `POST /api/v1/setup` remain an open self-service organization/workspace creation endpoint until auth exists, or should it become first-run-only once the first admin user exists?
    - Answer: 
3. For work item domain events, should the first implementation use in-process Spring events, persisted outbox events, or both from the beginning?
    - Answer: 
4. Should work item ranking use sortable rank strings, integer positions with gaps, or another ordering strategy?
    - Answer: 
5. Should work item sequence numbers be allocated strictly per project, or should there also be a workspace-wide sequence source for cross-project views/imports?
    - Answer: 
6. Should Workspace Admins have agent/repository management permissions by default, or should those stay limited to Workspace Owner and Agent Manager?
    - Answer: 
7. Which default custom fields and screens should be seeded for full product readiness beyond the core system fields?
    - Answer: 

## Current State

The backend currently has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and persistence tests in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, and provider-neutral AI agent execution.

The existing automated verification covers schema creation and representative persistence across the generated JPA model using PostgreSQL through Testcontainers. The first runnable backend slice now exposes an explicit setup API for creating an admin user, organization, workspace, project, memberships, and deterministic default project configuration. Public project reads are also wired through the first security configuration and still checked against workspace/project visibility in domain code.

## Primary Next Steps

### 1. Add Domain Validation And Work Item APIs

Goal: make seeded projects usable for real work while enforcing the hierarchy and workflow rules in `TECH_SPECS.MD`.

Planned work:

- Add work item create, read, update, list, assign, rank, and archive APIs.
- Implement validation for allowed parent-child work item type relationships.
- Prevent parent cycles.
- Reject cross-project parentage while allowing cross-project links.
- Reject work item types disabled for a project.
- Validate that a work item's status belongs to its assigned workflow.
- Implement workflow transition checks and transition history writes.
- Maintain `work_item_closure` rows when structural parentage changes.
- Publish domain events for work item changes so automation, audit, notifications, and reporting can subscribe later.
- Add tests for valid creation, invalid hierarchy, invalid status, invalid transition, assignment, ranking, closure rows, and emitted events.

Done when:

- A seeded project can create and manage work items through API calls.
- Work items cannot be created or moved into an invalid hierarchy or workflow state.
- Workflow transitions are checked by domain rules before persistence.
- Tests cover the high-risk validation paths called out in `TECH_SPECS.MD` against PostgreSQL.

### 2. Implement Authentication And Authorization Foundations

Goal: replace the temporary deny-by-default security posture with real identity, login, OAuth, and permission checks.

Planned work:

- Add username/password registration and login on the existing global user model.
- Add OAuth login through `user_auth_identities`.
- Add session or token strategy for API callers.
- Add workspace/project permission checks for setup-created memberships and roles.
- Keep anonymous public project reads available through the existing public route.
- Add tests for login, registration, OAuth identity linking, access denial, membership checks, and anonymous public reads.

Done when:

- Humans can authenticate without Spring's generated password behavior.
- Protected APIs require a valid identity and appropriate workspace/project permissions.
- Public projects remain readable anonymously only through explicitly public endpoints.

### 3. Replace Raw UUID Foreign-Key Handling With Richer Associations Where Useful

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

- Implement authentication and account creation according to the chosen first auth path.
- Add permission checks for workspace and project access.
- Add work item create, read, update, list, assign, rank, and delete/archive APIs.
- Add workflow transition APIs.
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

- Define project key and work item key sequencing behavior.
- Confirm whether Workspace Admin should receive agent/repository management permissions by default.
- Define default custom fields, screens, and screen assignments.
- Decide which remaining configuration is globally seeded, workspace-seeded, or project-seeded.

### Test Readiness

- Add integration tests for work item creation and updates.
- Add validation tests for hierarchy, workflow, permissions, and public read access.
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
