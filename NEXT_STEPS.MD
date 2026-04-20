# Trasck Next Steps

## Questions

Answer these as we start each implementation task. These are the remaining decisions that affect the next backend/frontend slices.

1. Should the sample import payloads become executable smoke-test fixtures/seed data, or remain documentation-only until the manual import flow stabilizes?
    - Answer: Keep them documentation-only for one manual pass, then promote the stable parts into executable smoke fixtures once we confirm the expected CSV/Jira/Rally flows. Add notes to the NEXT_STEPS.MD file to confirm the expected CSV/Jira/Rally flows and implement the smoke-test fixtures/seed data
2. Should import record diffs stay frontend-computed from snapshots, or should the backend expose normalized diff rows for API clients and audit exports?
    - Answer: Expose backend normalized diff rows
3. Should filtered bulk conflict resolution add batch/pagination safeguards before large imports, or is preview count plus exact confirmation enough for the first manual test?
    - Answer: Add batch/pagination safeguards now
4. Should open-conflict completion reasons become first-class audit/report fields, or remain domain-event payload metadata until audit reports are designed?
    - Answer: Make them first-class audit/report fields

## Current State

The backend has the full initial relational schema, Flyway migration, JPA entity model, JPA repositories, and PostgreSQL/Testcontainers verification in place. The schema is designed for the full Trasck product direction, including organizations, workspaces, projects, work item hierarchy, workflows, boards, planning, permissions, automation, reporting, notifications, attachments, imports, exports, audit data, durable domain events, and provider-neutral AI agent execution.

The first-run-only setup API creates an admin user, organization, workspace, project, memberships, deterministic default project configuration, and default local filesystem attachment storage. Public project reads are wired through security and still checked against workspace/project visibility in domain code.

Authentication and authorization foundations are implemented. Username/password login returns a JWT and sets the same value in an HTTP-only cookie. Direct API callers can use JWTs, revocable personal tokens, or revocable workspace service tokens as `Authorization: Bearer`. Cookie-authenticated unsafe browser requests use Spring cookie CSRF protection. Invitation-based registration supports workspace-only and workspace-plus-project membership. Workspace-admin direct user creation, signed verified OAuth identity linking, full Spring OAuth redirect startup for GitHub/Google/GitLab/Microsoft, provider-specific verified-email resolution, method-level admin permissions, explicit service-layer work item permission checks, enforced API token scopes, token list/revoke flows, dedicated service-token listing, token expiry coverage, and durable hybrid outbox delivery are in place.

The first work item API slices are implemented. Authenticated users with project/workspace permissions can create, read, update, cursor-list, assign, rank, transition, and archive work items through REST APIs. Create/update requests can include keyed `customFields`. Work item detail data includes comment create/update/delete/list, work item link create/delete/list, watcher add/remove/list, work log create/update/delete/list, workspace label create/list, work item label add/remove/list, attachment metadata APIs, and attachment file upload/download/delete through a provider-neutral storage interface. Local filesystem storage and S3-compatible provider selection are wired and tested. Work items support explicit team assignment through `teamId`; assigned teams must be active, belong to the workspace, and be attached to the project.

Team, planning, board, reporting, dashboard, saved filter, saved view, personalization, notification, automation, webhook, email, audit/export, and AI agent foundations are implemented enough for backend-driven manual testing. The Vite React frontend has route-based screens for the same major surfaces, cookie/CSRF browser auth, generated OpenAPI client tooling, feature-specific API services, global toasts/errors, detail routes, board card moves, admin/configuration flows, dashboard/filter/report flows, automation/worker/email/webhook flows, import flows, and agent provider/profile/repository/task flows.

Import jobs can parse CSV, Jira JSON, and Rally JSON into reviewable import records. Import records can be created, edited, filtered by `status`, `conflictStatus`, and `sourceType`, and inspected through append-only version history rows covering parse, edit, materialization, conflict-opened, and conflict-resolved lifecycle changes. The frontend computes adjacent field-level diff summaries from those snapshots. Import mapping templates can materialize records into work items through the normal work item service. Mapping templates support reusable workspace transform presets, immutable first-class transform preset version history, clone-from-version recovery into standalone presets, clone-and-retarget selected templates through separate preview/apply endpoints, template-local transformation overrides, legacy function names plus argument-based pipelines, custom target-field transform controls, reusable value lookups, type translations, and status translations. Each materialization creates an `import_materialization_runs` snapshot with the mapping template, mapping rules, transform preset, merged transformation config, counts, status, and actor/project metadata.

Re-running materialization with `updateExisting=false` skips existing imported targets and opens first-class conflicts instead of duplicating work items. Open conflicts can be listed with source payload/target details and resolved individually, in selected bulk batches, or by current filters as `skip`, `update_existing`, or `create_new`; filtered bulk resolution is gated by a preview/count, exact `expectedCount`, and `RESOLVE FILTERED CONFLICTS` typed confirmation. `update_existing` stages the record as pending for a later materialization/rerun, and unresolved `conflict` records are not processed. Snapshot reruns default to the source run's `updateExisting` flag but support an explicit admin override for modified reruns. Completing an import job with open conflicts is blocked unless the caller sends `acceptOpenConflicts=true`, the `COMPLETE WITH OPEN CONFLICTS` phrase, and an audit reason.

Local runtime and contract polish is in place. The backend has checked-in HTTP examples under `docs/http/`, realistic import samples under `docs/import-samples/`, local runtime guidance under `docs/LOCAL_RUNTIME.md`, a frontend-facing contract under `docs/api/FRONTEND_API_CONTRACT.md`, generated OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, a self-contained Docker Compose setup, `spring.jpa.open-in-view=false`, and the duplicate `org.json.JSONObject` dependency was removed. The Vite React repo now has `npm run generate:api`, checked-in generated OpenAPI JSON and TypeScript client, feature-specific API service wrappers, local API-base configuration, route-based navigation, cookie/CSRF browser auth without access-token local storage, global toast/error handling, admin/API token creation/listing screens, setup/login/current-user/project work item/team/planning/saved filter/saved view/dashboard/configuration/automation/import/agent provider/profile/repository/task flows, selector-fed common IDs, detail routes for configuration/planning/automation/import entities, card-level board drag/drop and manual move controls, worker retention export/prune/automatic schedule controls, transform preset CRUD, transform preset version-history display, clone-from-version and clone-retarget controls, mapping-template preset selection, custom target-field transform pipeline controls with JSON escape hatches, materialization run display, import record edit/version/diff forms, import conflict detail review/resolve/selected-bulk/filtered-bulk controls, exact/modified materialization rerun controls, typed open-conflict completion prompts, and Vitest/Testing Library route/component coverage.

## Primary Next Steps

### 1. Backend Reliability Follow-Through

Goal: keep the growing backend safe enough for a full manual product test.

Planned work:

- Keep adding migration/JPA coverage for every schema change.
- Expand permission tests for hierarchy, workflow, collaboration, user-management, team/planning, public read, dashboard/search/personalization, notification defaults, automation workers, import parsing/materialization/record editing/history/conflict resolution, worker history/health, attachment byte APIs, board-scoped commands, transform presets, and email provider settings.
- Extend activity/audit projection coverage for request-changes, waiting-for-input, retry failures, cancellation failures, notification default changes, worker run failures, automatic pruning, import record versions, import conflict resolution, snapshot reruns, clone-retarget, and external callback/delivery failures.
- Add provider-hosted API and hardened CLI-worker adapters behind the provider-neutral contract after the generic-worker protocol is exercised manually.

Done when:

- A full backend test run gives useful confidence that core product, reporting, admin, integration, import, worker, and agent flows still align with the spec.

### 2. Operations And Delivery Polish

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
- Manually test exact and modified import reruns against `docs/import-samples/` CSV/Jira/Rally payloads before expanding import update/delete semantics further.

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
- Expand import tests from parser/materialization coverage into disabled rule behavior, invalid transform arguments, update-existing edge cases, exact/modified rerun behavior, conflict update semantics, clone-and-retarget behavior, large filtered bulk conflict resolution, backend diff rows if chosen, guarded completion reporting, and deeper audit/activity assertions.
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
- Add UI polish for import sample-data walkthroughs and any backend-provided import diff rows after the open questions above are answered.
