# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should `update_existing` import conflict resolution execute the update immediately from the conflict endpoint, or keep staging the record as `pending` for the next materialization/rerun?
    - Answer: Stage as pending
2. Should import record editing get a dedicated update API before materialization, or remain parse/create-only until the source-mapper UX settles?
    - Answer: Give dedicated update API now
3. Should exact materialization snapshot reruns always reuse the source run's `updateExisting` flag, or allow an admin override when rerunning from the UI?
    - Answer: Both, default to use the existing updateExisting flag, but allow admins to override with explicitly in the UI/API as a modified rerun
4. Should transform preset clone-from-version keep creating standalone presets only, or should it optionally retarget selected mapping templates to the clone in the same operation?
    - Answer: Keep clone-only now, but add "Clone and retarget selected templates" into NEXT_STEPS.MD so that we can implement this later once the UI is mature enough to make that action clear

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team and planning APIs are implemented. Workspace admins can create/update/archive teams, manage immediate team membership joins/leaves, keep a single role value per project-team assignment, and manage workspace roadmaps. Project admins can assign teams to projects. Project users can list team/project-team data with read permission. Project board admins can create/update/archive boards, board columns, saved-filter/query-backed swimlanes, releases, release scope, project roadmaps, roadmap items, iterations, iteration scope, commitments, closes, and incomplete-work carryover. Boards expose per-column work item card data from configured status mappings plus backend swimlane groupings, and board-scoped rank/transition commands validate board membership before delegating to the normal work item command paths. Board transition commands still accept explicit `transitionKey`, drag/drop-style calls can derive the workflow transition from `targetColumnId` and/or `targetStatusId`, and the combined board move command can apply status transition plus card-level rank insertion in one request. Swimlanes can reference saved filter IDs directly while still supporting inline saved-filter/query-builder JSON for board-specific lanes. The browser board detail page renders draggable cards by column, supports card-level insertion drops, and exposes a manual move form with target column, previous/next, and transition-key controls. Backend and frontend tests cover card-level insertion behavior and invalid relative-card moves.

Reporting foundations include status, assignment, team, and estimate history writes plus dedicated read APIs for work item status history, assignment history, team history, estimate history, and work-log summaries. Project, workspace/project-set, and program dashboard summaries are implemented. Persisted snapshot jobs populate `cycle_time_records`, `iteration_snapshots`, `velocity_snapshots`, and `cumulative_flow_snapshots`, with manual run/backfill/reconcile controls, raw snapshot read APIs, report-shaped time-series DTOs, and live/snapshot iteration report APIs. Configurable snapshot retention policies, dedicated cycle-time/iteration/velocity/cumulative-flow rollup tables, a generic experimental rollup table, manual rollup/backfill controls, archive-run metadata, and additive `rollupSeries` project snapshot output are implemented.

Saved dashboard CRUD, widget CRUD, and render APIs are implemented for private, team, project, workspace, and public visibility. Saved filter CRUD, saved-filter work item execution, saved view CRUD, favorites, recent items, and the governed report query catalog are implemented. Workspace, project, and team scoped list endpoints exist for dashboards, saved filters, saved views, and report query catalog entries. Saved-filter execution uses a dedicated cursor-paged endpoint with boolean groups, multiple system/custom-field predicates, typed operator validation, and project-scoped permission coverage. Widget configs can reference saved filters or catalog entries while recursively rejecting raw SQL keys. Report query catalog `parametersSchema` is enforced at dashboard render time, and permission coverage includes project-only, team-member, non-team, and token-scope users for dashboard/filter/catalog/view sharing behavior.

Product configuration APIs are implemented. Workspace admins can manage custom field definitions, contexts, field configurations with project/type overrides, screen metadata, fields, and project/type/operation assignments. Work item editors can set/delete validated custom field values, and project work item lists can use typed searchable custom-field filters. Screen assignments enforce required system/custom fields during work item create/update, and command endpoints that mutate required system fields run targeted checks for the field they changed.

Activity and audit foundations are implemented. Static and configured durable consumers project domain events into cursor-page work-item/project/workspace activity streams, cursor-page workspace audit logs, and configured delivery rows. Tests cover durable outbox failure, retry, dead-letter, disabled consumers, unknown replay consumers, and multi-consumer delivery. Audit retention policy metadata is configurable per workspace; exports write JSON artifacts through configured attachment/object storage and create `export_jobs`, manual/scheduled pruning writes a stored export before deleting eligible rows, and workspace admins can list export jobs, inspect export metadata, and download stored export files.

Automation and integration execution have moved beyond shells. Automation rule execution creates queued jobs; admin worker endpoints process queued/failed jobs and expose succeeded/failed counts. Workspace-level worker settings can enable scheduled automation, webhook, and email workers while preserving manual admin endpoints, and worker run retention settings now support manual export/prune plus optional automatic scheduled pruning through configured attachment storage and `export_jobs`. Automatic worker-run pruning has its own fixed-delay scheduler, configurable interval throttle, optional workspace-timezone window, and last-start/last-finish metadata. Webhook actions create durable delivery rows, and webhook delivery processing supports inspect, retry, cancel, dry-run/real HTTP POST, bounded retry, and `dead_letter` status. Email actions create durable delivery rows using workspace-level Maildev or SMTP provider settings; SMTP passwords are encrypted at rest through the same database-backed secret-cipher direction as current agent credentials, with room to move both behind external secret management later. Maildev is blocked in production profiles. Worker processing writes dedicated `automation_worker_runs` history and `automation_worker_health` rows for automation, webhook, and email workers.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import mapping templates can materialize those records into work items through the normal work item service. Mapping templates now support reusable workspace transform presets, immutable first-class transform preset version history, clone-from-version recovery into standalone presets, template-local transformation overrides, legacy function names plus argument-based pipelines, reusable value lookups, type translations, and status translations, with REST/JPA coverage and integration tests proving materialization through those rules. Transform configs are validated on save, and each materialization creates an `import_materialization_runs` snapshot with the mapping template, mapping rules, transform preset, merged transformation config, counts, status, and actor/project metadata. Re-running materialization with `updateExisting=false` skips existing imported targets and opens first-class conflicts instead of duplicating work items. Open conflicts can be listed and resolved as `skip`, `update_existing`, or `create_new`; reparsing a source record clears stale conflict metadata. Exact rerun from a prior materialization run uses the stored mapping/template/rule/transform snapshots while applying the current import record conflict decisions.

The provider-neutral AI agent slice is implemented. Workspace admins and Agent Managers can create simulated, Codex, Claude Code, and generic-worker agent providers, list/deactivate/re-encrypt redacted provider credentials, rotate per-provider callback signing keys without exposing raw secrets, create assignable agent profiles backed by agent users, optionally scope profiles to projects, add repository connections, assign work items to agents, receive signed callbacks, create review artifacts/comments, accept agent results, and project agent events into activity/audit streams. Current adapters prove queued/running/waiting/review lifecycle behavior without shell commands or paid/external AI execution.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo now has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token creation/listing screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent provider/profile/repository/task flows, selector-fed common IDs, detail routes for configuration/planning/automation/import entities, card-level board drag/drop and manual move controls, worker retention export/prune/automatic schedule controls, transform preset CRUD, transform preset version-history display, clone-from-version controls, mapping-template preset selection, materialization run display, import conflict review/resolve controls, exact materialization rerun controls, and Vitest/Testing Library route/component coverage.

## Primary Next Steps

### 1. Import Manual-Test Follow-Through

Goal: make the hardened import flows comfortable enough for a complete browser test without relying on raw JSON for routine review and resolution.

Planned work:

- Add field-specific import record review/edit controls after deciding whether parsed records should be editable through a dedicated API.
- Add clearer conflict detail views showing source payload, current target reference, resolution state, and the next materialization/rerun action.
- Add UI support for transform pipelines and mapping-rule child records that keeps raw JSON as the advanced escape hatch.
- Expand permission, activity, and audit assertions around conflict resolution, clone-from-version, exact reruns, and update-existing rerun behavior.

Done when:

- A user can clone historical transform presets, inspect and resolve import conflicts, rerun from a materialization snapshot, and verify import results from the browser with only advanced transform pipelines requiring JSON.

### 2. Backend Reliability Follow-Through

Goal: keep the growing backend safe enough for a full manual product test.

Planned work:

- Keep adding migration/JPA coverage for every new schema change; the latest pass covers import conflict columns, mapping-rule snapshots, clone-from-version, exact reruns, materialization skipped/conflicted counters, board saved-filter swimlanes, combined board moves, and automatic pruning schedule metadata.
- Expand permission tests for hierarchy, workflow, collaboration, user-management, team/planning, public read, dashboard/search/personalization, notification defaults, automation workers, import parsing/materialization/conflict resolution, worker history/health, attachment byte APIs, board-scoped commands, transform presets, and email provider settings.
- Extend activity/audit projection coverage for request-changes, waiting-for-input, retry failures, cancellation failures, notification default changes, worker run failures, automatic pruning, import conflict resolution, snapshot reruns, and external callback/delivery failures.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.

Done when:

- A full backend test run gives useful confidence that core product, reporting, admin, integration, import, worker, and agent flows still align with the spec.

### 3. Operations And Delivery Polish

Goal: finish operational polish after the first import hardening pass.

Planned work:

- Add more permission, activity, audit, and dead-letter tests around email delivery, scheduled workers, retry/cancel failures, worker retention, SMTP settings, automatic pruning schedule/throttle behavior, and worker settings.
- Keep SMTP passwords database-encrypted for now, but preserve the secret-cipher abstraction so SMTP and agent credentials can move behind Vault/KMS/Secrets Manager later.
- Exercise configured webhook, email, automation, and generic-worker delivery flows manually before enabling any non-test external endpoint by default.

Done when:

- Admins can operate imports and delivery workers from APIs/UI without database access, conflict resolution is explicit, and email delivery has a production configuration path rather than relying on Maildev defaults.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, personalization/custom-field paths, automation workers, notification defaults, import parsing/materialization/conflict resolution, and worker history as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, notification defaults, worker runs, import conflict resolution, snapshot reruns, and external callback/delivery failures as those flows are added.
- Review dedicated automatic worker-run pruning interval/window defaults after manual worker history grows enough to validate cadence.
- Keep SMTP secrets database-encrypted until the shared external secret-management abstraction is introduced for both SMTP and non-stub agent credentials.
- Manually test exact import reruns against real CSV/Jira/Rally samples before expanding import update/delete semantics.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Keep multi-predicate custom-field filtering behind saved-filter/query-builder execution unless product usage proves direct project list query params need it too.
- Continue expanding targeted screen required-field checks only for command endpoints that can invalidate a required field.

### Test Readiness

- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, notification defaults, automation workers, worker history/health, import materialization/conflicts, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Expand automation tests for queued delivery retries, dead-letter handling, scheduled workers, worker settings, manual and automatic worker run retention schedule/window/throttle behavior, SMTP provider settings, and Maildev/SMTP email delivery.
- Expand import tests from parser/materialization coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, exact rerun override decisions, conflict update semantics, and deeper audit/activity assertions.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Keep normal browser auth on cookie plus CSRF with no browser access-token local storage.
- Use the route-based frontend shell and global toast/error handling as the manual-test surface.
- Use the checked-in generated TypeScript API client and regenerate it only when the backend contract changes.
- Keep feature-specific frontend API services as the boundary between UI components and the generated client.
- Replace raw JSON child editors with field-specific forms for common nested admin/import records.
- Expand frontend route tests and replace remaining raw ID inputs only after corresponding list endpoints exist.
- Keep board drag/drop card-level insertion covered in route/component tests as the board UI grows.
- Add UI for workspace email provider settings, import record editing, conflict details, and argument-based import transform pipelines beyond the current raw JSON advanced editors.
