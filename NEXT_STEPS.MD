# Trasck Next Steps

## Questions

Answer these as we start each implementation task. They are intentionally task-oriented so decisions can be made when the work is actually being built.

1. For the first runnable backend slice, should local development create a default organization, workspace, project, and admin user automatically, or should those be created only through explicit APIs?
    - Answer: Do not create these defaults when running locally, but make sure we have test coverage to ensure it works
2. Which authentication path should be implemented first: username/password, OAuth, or both behind a shared account model?
    - Answer: Both
3. Should anonymous read access for public projects be wired into the first security configuration, or should it be modeled in data first and enforced when public project APIs are added?
    - Answer: Wire into the first security config
4. What default workspace seed data should be created automatically: work item types, workflow statuses, workflow transitions, priorities, resolutions, boards, roles, or all of these?
    - Answer: All
5. Should the first work item API expose only REST DTOs, or should it also publish domain events for later automation, audit, notification, and reporting consumers?
    - Answer: Do everything
6. Which attachment storage provider should be implemented first for local development: filesystem, S3-compatible MinIO, or only a configurable storage interface?
    - Answer: Configurable, but default to the filesystem
7. Should integration test fixtures create a full default organization/workspace/project automatically, or should each test create exactly the data it needs?
    - Answer: Create defaults automatically and then clean up when the test runs are finished
8. Which frontend flow should be supported first for user testing: workspace setup, project setup, backlog management, board interaction, or issue detail editing?
    - Answer: Implement everything at once
9. Which AI agent provider should be implemented first as the reference adapter: generic webhook, local command runner, Codex, Claude Code, or a fake test provider?
    - Answer: Start with Codex since that's what I use currently
10. Which agent dispatch mode should be supported first: webhook push, agent polling, managed execution, or manual handoff?
    - Answer: Implement all of these, defaulting to manual handoff
11. For local development, should agent provider credentials be stored as encrypted database values, environment-backed secret references, or external vault references?
    - Answer: Encrypted DB values
12. Should agent completion always require human review at first, or should projects be allowed to configure automatic workflow transitions immediately?
    - Answer: Changes should always require a human sign-off before moving to the Done state, whether the story was completed by an AI Agent or a human developer. Once the approval stage is complete, then the workflow will be allowed to move to Done
13. Which repository integration should be implemented first for coding-agent context: GitHub, GitLab, generic Git repository URL, or repository metadata only?
    - Answer: Allow for GitHub and GitLab initially

## Current State

The backend currently has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and persistence tests in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, and planned provider-neutral AI agent execution.

The existing automated verification covers schema creation and representative persistence across the generated JPA model using PostgreSQL through Testcontainers. At this point, the backend is still mostly a persistence foundation. It does not yet expose the product workflows a user would exercise through an API or UI, and it does not yet implement the planned agent provider/task tables.

## Primary Next Steps

### 1. Add Service-Layer APIs For Workspace And Project Creation

Goal: make the foundational hierarchy usable through application code instead of raw repository access.

Planned work:

- Create service classes for organization, workspace, and project creation.
- Add DTOs and REST controllers for the initial setup flow.
- Enforce workspace-scoped project key uniqueness.
- Add deterministic seed data for newly created workspaces.
- Return complete setup responses that the frontend can use immediately.
- Add integration tests for creating the first organization, workspace, and project.

Done when:

- A local user can create the first usable Trasck workspace and project through API calls.
- The database contains the default configuration needed for work item creation.
- Tests prove the setup flow works against PostgreSQL.

### 2. Add Domain Validation Around Work Item Types And Workflow Transitions

Goal: enforce the rules that keep Trasck's hierarchy and workflow model coherent.

Planned work:

- Implement validation for allowed parent-child work item type relationships.
- Prevent parent cycles.
- Reject cross-project parentage while allowing cross-project links.
- Reject work item types disabled for a project.
- Validate that a work item's status belongs to its assigned workflow.
- Implement workflow transition checks and transition history writes.
- Add tests for invalid hierarchy, invalid status, invalid transition, and valid transition cases.

Done when:

- Work items cannot be created or moved into an invalid hierarchy.
- Workflow transitions are checked by domain rules before persistence.
- Tests cover the high-risk validation paths called out in `TECH_SPECS.MD`.

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

### 4. Add Provider-Neutral AI Agent Foundations

Goal: let work items be assigned to AI agents without making Trasck dependent on any one agent vendor or changing the normal Jira/Rally-style workflow.

Planned work:

- Add `account_type` support so global users can be human, agent, or service identities.
- Add provider-neutral agent tables for providers, credentials, agent profiles, repository connections, agent tasks, task events, messages, artifacts, and task repositories.
- Add an `AgentProviderAdapter` service contract that supports configuration validation, capability discovery, dispatch, cancel, retry, and callback handling.
- Add a fake/test provider first so the backend can prove lifecycle behavior without depending on Codex, Claude Code, or any external agent.
- Keep provider-specific behavior inside adapters selected by `provider_type`.
- Add permission keys for provider management, agent assignment, task cancellation/retry, log viewing, result acceptance, and repository connection management.
- Add audit and activity events for agent assignment, provider configuration, credential rotation, task status changes, messages, and artifacts.
- Add tests for assignment, dispatch, callback idempotency, cancellation, retry, completion, failure, artifact creation, and permission enforcement.

Done when:

- A work item can be assigned to a configured fake agent through the same assignment model used for humans.
- Agent execution state is visible and auditable without closing the work item automatically.
- A future provider such as Codex, Claude Code, a local runner, or a webhook worker can be added by implementing the adapter contract rather than changing work item or workflow logic.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Add explicit security configuration so local runs do not rely on Spring's generated password behavior.
- Implement authentication and account creation according to the chosen first auth path.
- Implement organization, workspace, project, and membership setup APIs.
- Add permission checks for workspace and project access.
- Add public project read handling for anonymous access.
- Add work item create, read, update, list, assign, rank, and delete/archive APIs.
- Add workflow transition APIs.
- Add comment, link, watcher, label, and basic attachment APIs.
- Add provider-neutral agent provider, agent profile, repository connection, and agent task APIs.
- Add a fake/test agent provider so local testing does not require paid or external AI services.
- Add authenticated, idempotent agent callback endpoints.
- Implement configurable attachment storage behind a stable interface.
- Add audit writes for security-sensitive and work-item-changing operations.
- Add audit writes for agent provider setup, credential rotation, agent assignment, privileged task actions, and callback failures.
- Add OpenAPI documentation or HTTP example files for the main workflows.
- Add Docker Compose support for running the backend with PostgreSQL in a predictable local setup.
- Clean up default warnings where practical, including generated security password behavior and open-in-view configuration.

### Product Configuration Readiness

- Define the default workspace work item types.
- Define default hierarchy rules.
- Define default priorities and resolutions.
- Define default workflow statuses and transitions.
- Define default board columns.
- Define default roles and permission assignments.
- Define agent-related permissions and decide which default roles can manage providers, assign agents, cancel tasks, retry tasks, view logs, and accept results.
- Define default agent review behavior. The recommended default is that agent completion requests human review instead of closing work automatically.
- Define project key and work item key sequencing behavior.
- Decide which configuration is globally seeded, workspace-seeded, or project-seeded.

### Test Readiness

- Add integration tests for the full setup flow.
- Add integration tests for work item creation and updates.
- Add validation tests for hierarchy, workflow, permissions, and public read access.
- Add migration tests whenever the schema changes.
- Add repository/query tests for list screens and detail pages.
- Add attachment storage tests with the first local provider.
- Add automation tests once the first automation executor exists.
- Add fake-agent provider tests covering dispatch, callback, messages, artifacts, cancellation, retry, failure, completion, and idempotency.
- Add agent permission tests proving agents cannot exceed their workspace/project role.
- Add secret-redaction tests proving provider credentials do not leak into context snapshots, comments, or activity events.
- Add reporting snapshot tests after work item updates begin writing reportable state.

### Frontend Readiness

- Connect the Vite React app to the backend through a typed API client.
- Add a local environment configuration for backend base URL.
- Build the first setup flow for organization, workspace, and project creation.
- Build basic project navigation.
- Build backlog list and work item detail screens.
- Build a minimal board view once workflow transitions are available.
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
