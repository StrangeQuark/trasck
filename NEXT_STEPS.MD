# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. After the manual CSV/Jira/Rally import pass, should executable smoke fixtures cover parser output only, or full parse-to-materialize/conflict/rerun flows?
    - Answer: Cover full parse-to-materialize/conflict/rerun flows
2. For filtered conflict sets above the current server batch cap, should the frontend guide admins through paginated selected-record batches, or should the backend add asynchronous bulk resolution jobs?
    - Answer: Add async bulk resolution jobs backend
3. Should backend import diff support stay per-record for now, or add job-level diff/export endpoints for audit exports and review screens?
    - Answer: Add job-level diff/export endpoints for audit exports and review screens
4. Should open-conflict completion fields be surfaced in reporting/dashboard summaries now, or wait until the broader audit-report screens are designed?
    - Answer: Surface them now, and add steps in NEXT_STEPS.MD to implement the broader screens

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered by `status`, `conflictStatus`, and `sourceType`, and inspected through append-only version history rows covering parse, edit, materialization, conflict-opened, and conflict-resolved lifecycle changes. The backend exposes normalized adjacent field-level diff rows for those versions, and the frontend now displays the backend-owned diff rows beside raw snapshots. Import mapping templates can materialize records into work items through the normal work item service. Mapping templates support reusable workspace transform presets, immutable first-class transform preset version history, clone-from-version recovery into standalone presets, clone-and-retarget selected templates through separate preview/apply endpoints, template-local transformation overrides, legacy function names plus argument-based pipelines, custom target-field transform controls, reusable value lookups, type translations, and status translations. Each materialization creates an `import_materialization_runs` snapshot with the mapping template, mapping rules, transform preset, merged transformation config, counts, status, and actor/project metadata.

Re-running materialization with `updateExisting=false` skips existing imported targets and opens first-class conflicts instead of duplicating work items. Open conflicts can be listed with source payload/target details and resolved individually, in selected bulk batches, or by current filters as `skip`, `update_existing`, or `create_new`; filtered bulk preview is paginated, reports total matched rows and returned preview rows, and filtered apply is gated by exact `expectedCount`, `RESOLVE FILTERED CONFLICTS` typed confirmation, and a server-side batch cap. `update_existing` stages the record as pending for a later materialization/rerun, and unresolved `conflict` records are not processed. Snapshot reruns default to the source run's `updateExisting` flag but support an explicit admin override for modified reruns. Completing an import job with open conflicts is blocked unless the caller sends `acceptOpenConflicts=true`, the `COMPLETE WITH OPEN CONFLICTS` phrase, and an audit reason; successful open-conflict completion persists first-class count/actor/time/reason fields on `import_jobs`.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo now has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token creation/listing screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent provider/profile/repository/task flows, selector-fed common IDs, detail routes for configuration/planning/automation/import entities, card-level board drag/drop and manual move controls, worker retention export/prune/automatic schedule controls, transform preset CRUD, transform preset version-history display, clone-from-version and clone-retarget controls, mapping-template preset selection, custom target-field transform pipeline controls with JSON escape hatches, materialization run display, import record edit/version/backend-diff forms, import conflict detail review/resolve/selected-bulk/filtered-bulk controls, exact/modified materialization rerun controls, typed open-conflict completion prompts, and Vitest/Testing Library route/component coverage.

## Primary Next Steps

### 1. Manual Import Sample Validation And Fixture Promotion

Goal: use the checked-in sample payloads for one manual pass, then promote stable expectations into executable smoke coverage.

Planned work:

- Manually exercise `docs/import-samples/` CSV, Jira JSON, and Rally JSON payloads through parse, mapping-template setup, materialization, conflict opening/resolution, and exact/modified rerun paths.
- Record expected parse counts, important field mappings, translated type/status values, visibility/value lookup behavior, materialized titles, conflict behavior, backend diff rows, and guarded completion fields.
- Promote the stable parts into executable smoke fixtures/seed data after the manual pass, using the answer to the parser-only vs full-flow question above.
- Keep the sample payloads documentation-only until that manual pass confirms the expected CSV/Jira/Rally flows.

Done when:

- The sample import walkthrough has documented expected outputs and the agreed stable flows are covered by repeatable smoke fixtures.

### 2. Large Import Operations Polish

Goal: make large import review safe and ergonomic after the first manual import pass.

Planned work:

- Add either a frontend guided selected-batch workflow or backend asynchronous bulk resolution jobs for filtered conflict sets that exceed the current server-side batch cap.
- Decide whether backend normalized diffs need job-level/export endpoints or whether per-record diff rows are enough for the next manual test.
- Decide whether open-conflict completion fields should feed dashboard/report summaries now or wait for audit-report screens.
- Replace raw JSON diff previews with field-specific diff tables once the manual review flow is stable.

Done when:

- Large imports can be reviewed, diffed, and conflict-resolved without forcing direct database access or risky unbounded API operations.

### 3. Operations And Delivery Polish

Goal: finish operational polish after the import hardening pass.

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
- Expand permission checks and permission tests beyond work items, dashboards, user-management, team/planning, agent endpoints, personalization/custom-field paths, automation workers, notification defaults, import parsing/materialization/record editing/history/conflict resolution, and worker history as more APIs are exposed.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.
- Extend activity/audit projection coverage to request-changes, waiting-for-input, retry failures, cancellation failures, notification defaults, worker runs, import record versions, import conflict resolution, clone-retarget, snapshot reruns, and external callback/delivery failures as those flows are added.
- Review dedicated automatic worker-run pruning interval/window defaults after manual worker history grows enough to validate cadence.
- Keep SMTP secrets database-encrypted until the shared external secret-management abstraction is introduced for both SMTP and non-stub agent credentials.
- Manually test exact and modified import reruns against `docs/import-samples/` CSV/Jira/Rally payloads, record expected outputs, and then promote stable sample flows into smoke fixtures before expanding import update/delete semantics further.

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
- Expand import tests from parser/materialization coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, exact/modified rerun behavior, conflict update semantics, clone-and-retarget behavior, large filtered bulk conflict resolution beyond the current cap, sample-payload smoke fixtures, guarded completion reporting, and deeper audit/activity assertions.
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
- Add UI polish for import sample-data walkthroughs and backend-provided import diff rows after the manual import review flow stabilizes.
