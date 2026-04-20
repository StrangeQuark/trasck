# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Before enabling any non-stub hosted API or CLI-worker agent execution, should Trasck add a persisted `agent_dispatch_attempts` table, or are `agent_task_events` enough for the next hardening pass?
    - Answer: Add a persisted agent_dispatch_attempts table now
2. Should backend-generated import CSV export jobs expand next to conflict/job/completion review tables, or stay limited to job-level version diffs until larger import testing proves the need?
    - Answer: Let them expand now
3. Should worker-type filters remain list/read-only for operations screens, or should retention export/prune also accept worker-type filters?
    - Answer: Allow retention export/prune to also accept worker-type filters

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered by `status`, `conflictStatus`, and `sourceType`, and inspected through append-only version history rows covering parse, edit, materialization, conflict-opened, and conflict-resolved lifecycle changes. The backend exposes normalized adjacent field-level diff rows for record versions and grouped job-level diff/export responses for audit review; the frontend displays those rows in field-specific record/job diff tables beside raw snapshots. Import mapping templates can materialize records into work items through the normal work item service. Mapping templates support reusable workspace transform presets, immutable first-class transform preset version history, clone-from-version recovery into standalone presets, clone-and-retarget selected templates through separate preview/apply endpoints, template-local transformation overrides, legacy function names plus argument-based pipelines, custom target-field transform controls, reusable value lookups, type translations, and status translations. Each materialization creates an `import_materialization_runs` snapshot with the mapping template, mapping rules, transform preset, merged transformation config, counts, status, and actor/project metadata.

Re-running materialization with `updateExisting=false` skips existing imported targets and opens first-class conflicts instead of duplicating work items. Open conflicts can be listed with source payload/target details and resolved individually, in selected bulk batches, by current filters as `skip`, `update_existing`, or `create_new`, or through queued filtered conflict-resolution jobs for sets larger than the synchronous cap. Filtered bulk preview is paginated, reports total matched rows and returned preview rows, and filtered sync/queued apply is gated by exact `expectedCount` and `RESOLVE FILTERED CONFLICTS` typed confirmation. `update_existing` stages the record as pending for a later materialization/rerun, and unresolved `conflict` records are not processed. Queued conflict-resolution jobs can be listed by import job or workspace, run manually, canceled, retried, processed by a bounded workspace worker endpoint, or picked up by the shared scheduled worker when `automation_worker_settings.import_conflict_resolution_enabled` is enabled for that workspace. Snapshot reruns default to the source run's `updateExisting` flag but support an explicit admin override for modified reruns. Completing an import job with open conflicts is blocked unless the caller sends `acceptOpenConflicts=true`, the `COMPLETE WITH OPEN CONFLICTS` phrase, and an audit reason; successful open-conflict completion persists first-class count/actor/time/reason fields on `import_jobs` and feeds project/workspace dashboard summary import-completion metrics plus dedicated import-completion reporting endpoints/widgets. Checked-in CSV/Jira/Rally sample payloads are covered by a full parse-to-materialize/conflict/queued-resolution/cancel/retry/failure/exact-rerun/modified-rerun/completion fixture, and workspace admins can create backend-generated sample jobs from the same sample catalog through the import API/UI.

Import demo job endpoints remain workspace-admin-only. Local/default runtimes default sample jobs on for developer walkthroughs but workspace admins can toggle `sampleJobsEnabled` per workspace through import settings; production-like `prod`, `production`, `staging`, and `hosted` profiles require both the workspace setting and explicit deployment enablement with `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED=true`. Import conflict-resolution worker executions now write dedicated `automation_worker_runs` and `automation_worker_health` rows with worker type `import_conflict_resolution`, so scheduled and manual pickup are visible through the same operations APIs as automation, webhook, and email workers; the frontend operations view can filter worker run/health previews by worker type. The frontend import review tables for record diffs, job diffs, export artifacts, conflict-resolution jobs, and completion metrics include column-scoped filtering; job-level version-diff CSV exports now create backend export-job artifacts using the active filter state, while smaller local review tables still support client-side CSV export.

Agent delivery hardening now keeps the generic worker protocol as the main extensibility path while Codex and Claude Code adapters expose explicit `stub`, `hosted_api`, and hardened `cli_worker` runtime modes behind the provider-neutral adapter contract. External execution must be intentionally enabled in provider config; CLI-worker config accepts named allowlisted command profiles instead of raw shell commands, and adapter contract tests cover hosted/CLI payload boundaries without invoking external systems.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo now has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token creation/listing screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent provider/profile/repository/task flows, selector-fed common IDs, detail routes for configuration/planning/automation/import entities, card-level board drag/drop and manual move controls, worker retention export/prune/automatic schedule controls, worker-type filtered operations previews, import conflict scheduled-worker settings, transform preset CRUD, transform preset version-history display, clone-from-version and clone-retarget controls, mapping-template preset selection, custom target-field transform pipeline controls with JSON escape hatches, materialization run display, sample import walkthrough controls, import workspace sample settings, import record edit/version/backend-diff forms, field-specific import record/job diff tables, backend-generated filtered job-diff CSV export jobs, import diff export artifact history/download controls, conflict-resolution job audit tables, import completion metrics tables, import conflict detail review/resolve/selected-bulk/filtered-bulk/queued-job cancel/retry/worker controls, exact/modified materialization rerun controls, typed open-conflict completion prompts, and Vitest/Testing Library route/component coverage.

## Primary Next Steps

### 1. End-To-End Operations Validation

Goal: validate the now-operable automation, webhook, email, import, and worker-retention surfaces against realistic local data before public testing.

Planned work:

- Exercise configured webhook, email, automation, import conflict-resolution, worker retention, and generic-worker delivery flows manually through the APIs/UI without database access.
- Add focused permission, activity, audit, retry/cancel, dead-letter, and retention tests where the manual pass finds fragile or under-covered behavior.
- Keep SMTP passwords database-encrypted for now, while preserving the secret-cipher abstraction so SMTP and agent credentials can move behind Vault/KMS/Secrets Manager later.

Done when:

- A local tester can operate delivery workers, inspect failures, retry/cancel jobs, export/prune worker history, create guarded sample imports, and review/import conflict outcomes without touching the database.

### 2. Agent Dispatch Persistence And External Enablement

Goal: prepare real hosted API or CLI-worker execution without making normal tests or local development call external systems.

Planned work:

- Decide whether dispatch attempts need a dedicated durable table before any non-stub execution is enabled.
- Add admin-facing validation/preview for Codex/Claude hosted API and CLI-worker runtime config.
- Keep generic-worker compatibility as the fallback protocol for future providers.

Done when:

- A workspace admin can inspect exactly what would be sent to a non-stub adapter, prove idempotency/retry/cancel behavior at the boundary, and enable real execution only through explicit configuration.

## Remaining Dashboard/Report APIs

- Time and estimate reporting by user/project/work item, remaining-time trends, and estimate-vs-actual summaries.
- Automatic destructive snapshot pruning after manual rollup behavior is reviewed.

## Before Full User Testing

The following work should be completed before the application is ready for a meaningful end-to-end test by a user.

### Backend Readiness

- Keep manual reporting rollups stable and add automatic destructive snapshot pruning only after dashboard behavior has been manually reviewed.
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, personalization/custom-field paths, automation workers, notification defaults, import parsing/materialization/record editing/history/conflict resolution, and worker history as more APIs are exposed.
- Add persisted dispatch attempts or stronger agent-task event projections before any real hosted API or CLI-worker execution is enabled, depending on the answered question above.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, notification defaults, worker runs, import record versions, clone-retarget, snapshot reruns, and external callback/delivery failures as those flows are added.
- Review dedicated automatic worker-run pruning interval/window defaults after manual worker history grows enough to validate cadence.
- Keep SMTP secrets database-encrypted until the shared external secret-management abstraction is introduced for both SMTP and non-stub agent credentials.
- Manually test a larger real-world import set through the UI, including field diff tables, backend-generated job-diff CSV export artifacts, smaller client-side CSV table exports, export artifact downloads, scheduled conflict pickup, and guarded sample job creation, before expanding import update/delete semantics further.

### Product Configuration Readiness

- Decide which remaining configuration after core system fields is globally seeded, workspace-seeded, or project-seeded.
- Keep multi-predicate custom-field filtering behind saved-filter/query-builder execution unless product usage proves direct project list query params need it too.
- Continue expanding targeted screen required-field checks only for command endpoints that can invalidate a required field.

### Test Readiness

- Add migration tests whenever the schema changes.
- Add repository/query tests for high-use list screens and detail pages.
- Expand permission tests for hierarchy, workflow, collaboration APIs, user management, team/planning edge cases, public read access, dashboard/search/personalization sharing, notification defaults, automation workers, worker history/health, import materialization/conflicts/record editing/history, and attachment byte APIs.
- Add provider-specific OAuth callback tests, token revocation edge cases, and invitation expiry tests.
- Expand automation tests for queued delivery retries, dead-letter handling, scheduled workers, worker settings, manual and automatic worker run retention schedule/window/throttle behavior, SMTP provider settings, and Maildev/SMTP email delivery.
- Expand import tests from parser/materialization/sample-fixture/backend-sample coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, conflict update semantics, production-profile sample gating, guarded completion reporting screens, and field-specific import review UI assertions.
- Expand agent adapter contract tests covering callback validation, messages, artifacts, failure, completion, and idempotency without invoking paid or external execution.
- Expand agent permission tests across custom roles and project-scoped profiles.
- Add secret-redaction tests proving provider credentials and callback private keys do not leak into context snapshots, comments, activity events, audit entries, or API responses.
- Expand reporting snapshot tests for persisted status, assignment, estimate, and time-tracking history once those additional snapshot families exist.

### Frontend Readiness

- Keep normal browser auth on cookie plus CSRF with no browser access-token local storage.
- Use the route-based frontend shell and global toast/error handling as the manual-test surface.
- Use the checked-in generated TypeScript API client and regenerate it only when the backend contract changes.
- Keep feature-specific frontend API services as the boundary between UI components and the generated client.
- Replace remaining raw JSON child editors with field-specific forms for common nested admin/import records.
- Expand frontend route tests and replace remaining raw ID inputs only after corresponding list endpoints exist.
- Keep board drag/drop card-level insertion covered in route/component tests as the board UI grows.
- Exercise import sample-data walkthroughs, import workspace sample settings, field-specific import diff tables, column filters, backend-generated job-diff CSV export jobs, remaining client-side CSV table exports, import diff export artifact downloads, and import completion dashboard screens during manual end-to-end testing.
