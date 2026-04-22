# Trasck Manual Test Cases

Use this file as the primary human test script before the first broad manual bug-fix loop. Record detailed defects in `MANUAL_TEST_NOTES.MD`; use the result blocks here only for pass/fail status, quick notes, and defect IDs.

## Test Session Setup

Recommended local services:

```bash
cd "/home/strangequark/Desktop/codex - trasck/trasck"
docker compose up -d
./mvnw spring-boot:run
```

```bash
cd "/home/strangequark/Desktop/codex - trasck/trasck-frontend"
npm run dev
```

Default local URLs:

- Backend: `http://localhost:6100`
- Frontend: `http://localhost:5173`
- Maildev UI: `http://localhost:1080`

Codex/Claude manual agent prerequisites:

- Use only a disposable repository checkout.
- Set `TRASCK_AGENTS_CLI_WORKER_ENABLED=true`.
- Set `TRASCK_CODEX_AGENT_WORKDIR` and `TRASCK_CLAUDE_CODE_AGENT_WORKDIR` to disposable checkouts.
- Ensure the `codex` and `claude` CLIs are installed and authenticated on the backend host.
- Local default command profiles are edit-capable: `codex exec --full-auto -` and `claude -p {prompt} --permission-mode bypassPermissions`.

For every test, capture:

- Result: `Pass / Fail / Blocked / Not Run`
- Notes: short observations only
- Defect IDs: IDs or links in `MANUAL_TEST_NOTES.MD`

## Environment And Bootstrap

### TC-001 - Local Stack Starts Cleanly

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open a terminal in `trasck`.
2. Run `docker compose up -d`.
3. Run `docker compose ps`.
4. Confirm PostgreSQL, Redis, and Maildev containers are running.
5. Start the backend with `./mvnw spring-boot:run`.
6. Open `http://localhost:6100/api/trasck/health`.
7. Confirm the backend health response is successful.
8. Start the frontend from `trasck-frontend` with `npm run dev`.
9. Open `http://localhost:5173`.
10. Confirm the Trasck shell loads without a browser console error.

### TC-002 - First-Run Setup Creates Workspace

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open the frontend.
2. Navigate to `/setup`.
3. In `Initial Setup`, enter an admin email, username, display name, and password.
4. Enter organization, workspace, and project names and keys.
5. Click `Create`.
6. Confirm setup returns IDs for admin user, workspace, project, and seed data.
7. Store the workspace ID and project ID for later tests.
8. Navigate to `/auth`.
9. Log in with the new admin credentials.
10. Confirm `Current User` shows the admin user.

### TC-003 - First-Run Setup Is Locked After Bootstrap

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Stay logged in as the admin.
2. Navigate to `/setup`.
3. Fill the setup form with different organization/workspace/project values.
4. Click `Create`.
5. Confirm the request is rejected because setup has already run.
6. Confirm the original workspace and project still load.

### TC-004 - Browser Auth Uses Cookie And CSRF

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Navigate to `/auth`.
2. Click `Log out` if already signed in.
3. Sign in again using the admin credentials.
4. Open browser developer tools.
5. Inspect Application or Storage.
6. Confirm there is no access token stored in local storage.
7. Confirm an auth cookie is present.
8. Click a write action, such as creating a work item later in TC-013.
9. In the Network tab, confirm unsafe browser requests include the CSRF header.

## Admin, Security, And Roles

### TC-005 - Personal API Token Lifecycle

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `API tokens` from the navigation or Overview page.
2. In `Personal Token`, enter a clear token name.
3. Click `Create token`.
4. Confirm a token value is shown once.
5. Confirm the token value is masked or can be cleared from the screen.
6. Click `Load` in `Tokens`.
7. Confirm the token appears without the raw secret.
8. Revoke the token from the table.
9. Click `Load` again and confirm the token is inactive or absent.

### TC-006 - Workspace Service Token Lifecycle

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Stay on `API tokens`.
2. In `Service Token`, select or enter a workspace role.
3. Enter a token name and optional expiry.
4. Click `Create token`.
5. Confirm the service token secret is shown once.
6. Click `Load`.
7. Confirm service-token-backed users are shown in the token/admin surface, not mixed into normal member lists unless explicitly intended.
8. Revoke the token.
9. Confirm the revoked token cannot be used for a simple backend API request.

### TC-007 - System Admin Controls

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `System`.
2. Click `Load` in `System Admins`.
3. Confirm the current admin is listed as active.
4. Create a second user from Workspace Settings in TC-008 if one is not available.
5. Enter the second user's ID in `System Admins`.
6. Click `Grant`.
7. Confirm the second user appears as active.
8. Click the revoke icon for the second user.
9. Confirm revoke succeeds.
10. Attempt to revoke the only remaining active system admin.
11. Confirm the last-active-admin protection blocks it.

### TC-008 - Workspace Members And Invitations

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Workspace Settings`.
2. In `Workspace Members`, click `Load`.
3. Confirm the current admin appears.
4. Use the direct user form to create a human user with email, username, display name, password, and role.
5. Click the create-user button.
6. Confirm the new user appears in the member table.
7. In `Workspace Invitations`, enter an email and role.
8. Click the invite button.
9. Click `Load` in invitations.
10. Confirm the invitation appears as pending.
11. Click the revoke icon.
12. Confirm the invitation is revoked or removed from pending.
13. Remove the direct user from the workspace.
14. Confirm the member is removed without deleting the admin.

### TC-009 - Workspace Security Policy

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Workspace Settings`.
2. In `Workspace Security Policy`, click `Load`.
3. Toggle anonymous read enabled off.
4. Set attachment, import, and export byte limits to reasonable local values.
5. Click `Save`.
6. Click `Load` again.
7. Confirm the saved values persist.
8. Toggle anonymous read enabled on.
9. Click `Save`.
10. Confirm public preview tests can use workspace-level anonymous read later.

### TC-010 - Project Security Policy And Public Preview

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Project Settings`.
2. In `Project Security Policy`, click `Load`.
3. Set project visibility to `public`.
4. Set any project-level attachment/import/export overrides you want to test.
5. Click `Save`.
6. Click `Public Preview`.
7. Confirm the public preview opens in a new tab.
8. Confirm public project data loads when workspace anonymous read is enabled.
9. Return to `Project Settings`.
10. Set visibility back to `private`.
11. Click `Save`.
12. Refresh the public preview tab and confirm it no longer exposes private data.

### TC-011 - Workspace Role Management

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Workspace Settings`.
2. In `Workspace Roles`, click `Load roles`.
3. Select a seeded role.
4. Click `Refresh role`.
5. Confirm permission checkboxes and role metadata load.
6. Create a custom role from checked permissions.
7. Select the custom role.
8. Change its name or description.
9. Click `Preview impact`.
10. Confirm impacted members/invitations are shown.
11. Click `Save confirmed permissions`.
12. Confirm the save succeeds.
13. Click role version history.
14. Roll back to a previous version.
15. Archive the custom role when it is unassigned.

### TC-012 - Project Role Management

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Project Settings`.
2. In `Project Roles`, click `Load roles`.
3. Select a project role.
4. Click `Refresh role`.
5. Create a custom project role.
6. Preview permission impact.
7. Save confirmed permissions.
8. Confirm the role appears with project scope.
9. Roll back one version.
10. Archive the custom role after confirming it is unassigned.

## Work Items And Collaboration

### TC-013 - Create And Load Work Items

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Work items`.
2. In `Project Work`, click `Load`.
3. Enter a title, type key `story`, and description.
4. Click `Create`.
5. Confirm the new work item appears in the list.
6. Click or open the work item detail route if available from the list.
7. Confirm title, key, status, reporter, and description are correct.

### TC-014 - Update Work Item Fields And Commands

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open the work item from TC-013.
2. Update title, description, priority, due date, or estimate fields available on the page.
3. Save the work item.
4. Click `Load` or refresh detail.
5. Confirm updates persist.
6. Use transition controls to move from Open to Ready and then to In Progress if available.
7. Assign or reassign the item if assignee controls are available.
8. Use rank controls or board movement later to confirm rank changes.

### TC-015 - Comments Edit And Delete

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open a work item detail page.
2. In `Comments`, enter a comment.
3. Click the add comment button.
4. Confirm the comment appears.
5. Click the edit icon on the comment.
6. Change the text.
7. Save the edit.
8. Confirm the edited text appears.
9. Click the delete icon.
10. Confirm the comment is removed.

### TC-016 - Links And Watchers

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create a second work item from `Work items`.
2. Open the first work item detail.
3. In `Links`, select or enter the second work item as target.
4. Select a link type.
5. Click add link.
6. Confirm the link appears.
7. Delete the link.
8. In `Watchers`, click add self as watcher.
9. Confirm your user appears in watchers.
10. Remove yourself as watcher.
11. Confirm the watcher row is removed.

### TC-017 - Work Logs And Labels

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open a work item detail.
2. In `Work Logs`, enter time spent and a description.
3. Click add work log.
4. Confirm the work log appears.
5. Edit the work log description or time.
6. Save and confirm the row updates.
7. Delete the work log.
8. In `Labels`, create a workspace label with name and color.
9. Attach the label to the work item.
10. Confirm the label appears on the work item.
11. Remove the label from the work item.
12. Delete the workspace label if it is no longer attached.

### TC-018 - Attachments Upload Download Delete

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create a small local text file.
2. Open a work item detail.
3. In `Attachments`, choose the file from the file picker.
4. Click upload.
5. Confirm attachment metadata appears.
6. Click `Download`.
7. Confirm the downloaded file content matches.
8. Click the delete icon.
9. Confirm the attachment row is removed.
10. Confirm public preview later does not expose private attachments.

### TC-019 - Activity And Audit Projection

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Make at least one work item change, comment, label, and attachment change.
2. Open the work item detail.
3. In `Activity`, click `Load` or refresh.
4. Confirm activity rows include the recent changes.
5. Navigate to `System` or the audit/export surface.
6. Load workspace audit entries.
7. Confirm relevant admin and work item actions appear.
8. Confirm raw secrets or private keys are not shown in activity or audit rows.

## Configuration

### TC-020 - Custom Field Definition And Context

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Config`.
2. In `Custom Field`, enter a field name, key, and field type.
3. Click `Create field`.
4. Confirm the field appears in configuration records.
5. In `Context`, select the custom field.
6. Enter a default JSON value if applicable.
7. Click `Add context`.
8. Confirm context appears.
9. Open the field detail route if available.
10. Confirm the field and context can be reloaded.

### TC-021 - Field Configuration Required-Field Enforcement

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Config`.
2. In `Field Configuration`, select the custom field from TC-020.
3. Mark the field required in validation JSON or field-specific controls.
4. Click `Create config`.
5. Navigate to `Work items`.
6. Attempt to create or update a work item without the required field.
7. Confirm the backend rejects the request with a clear validation error.
8. Add the required custom field value.
9. Save again.
10. Confirm the work item saves.

### TC-022 - Screens And Screen Assignment

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Config`.
2. In `Screen`, create a screen.
3. In `Screen Layout`, add a system field.
4. Add the custom field from TC-020.
5. Assign the screen to the current project and work item type.
6. Open the screen detail route.
7. Save a metadata change.
8. Reload and confirm the layout and assignment persist.

## Planning, Boards, Releases, And Roadmaps

### TC-023 - Team CRUD And Project Assignment

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Teams` or `Planning`.
2. In `Teams`, enter a team name and key.
3. Click `Create team`.
4. Confirm the team appears in planning records.
5. Add the current user as a team member if controls are available.
6. In `Project Team`, select the team.
7. Click `Assign`.
8. Confirm the team is attached to the project.
9. Assign a work item to the team from the work item command or board/planning surface.

### TC-024 - Iteration Planning

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Planning`.
2. In `Iterations`, enter an iteration name and dates.
3. Click `Create`.
4. Confirm the iteration appears.
5. Add work items to iteration scope if controls are present.
6. Commit the iteration.
7. Close the iteration.
8. Confirm carryover behavior is visible or recorded.

### TC-025 - Board Columns And Swimlanes

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Planning`.
2. In `Board`, create a board.
3. In `Board Layout`, add at least three columns.
4. Map columns to statuses.
5. Add a swimlane using a simple field/value predicate or saved-filter reference if available.
6. Click `Load` in `Planning Records`.
7. Confirm board, columns, and swimlanes appear.
8. Open the board detail route.
9. Confirm the layout reloads.

### TC-026 - Board Card Move And Drag/Drop

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Ensure at least two work items exist.
2. Open the board detail route.
3. Confirm cards render in the expected columns.
4. Drag a card from one column to another.
5. Confirm the card moves and status updates.
6. Drag a card above or below another card in the same target column.
7. Confirm rank/order changes without page refresh.
8. Use `Move Card` controls for the same card.
9. Confirm manual move and drag/drop paths produce the same result.

### TC-027 - Releases

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Planning`.
2. In `Release`, enter name, version, and dates.
3. Click `Create release`.
4. Confirm the release appears.
5. Add a work item to release scope.
6. Open the release detail route.
7. Edit release metadata.
8. Reload and confirm updates persist.
9. Archive the release.
10. Confirm archived release no longer appears in active lists.

### TC-028 - Roadmaps

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Planning`.
2. In `Roadmap`, create a roadmap.
3. Add a work item or release item to the roadmap.
4. Click `Load`.
5. Confirm roadmap and item appear.
6. Open roadmap detail.
7. Edit roadmap configuration.
8. Reload and confirm changes persist.
9. Archive the roadmap.
10. Confirm archived roadmap is no longer active.

## Search, Dashboards, Reports, And Programs

### TC-029 - Saved Filters And Query Execution

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Filters`.
2. Create a saved filter for current project work items.
3. Include at least one system predicate.
4. Include one custom-field predicate if TC-020 is complete.
5. Click create or save.
6. Execute the saved filter.
7. Confirm cursor-paged work item results are returned.
8. Confirm invalid predicates show clear validation errors.

### TC-030 - Saved Views, Favorites, And Recent Items

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Filters` or the personalization route that exposes saved views.
2. Create a saved view for a work item list.
3. Mark a work item, dashboard, or filter as favorite.
4. Open several work items or dashboards to generate recent items.
5. Reload saved views, favorites, and recent items.
6. Confirm records persist for the current user.
7. Remove a favorite.
8. Delete a recent item.
9. Confirm both changes persist.

### TC-031 - Dashboard Builder And Render

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Dashboards`.
2. In `Dashboard Builder`, create a dashboard.
3. In `Widget`, add at least one work item or import completion widget.
4. Click `Load` in `Render`.
5. Select or enter the dashboard ID.
6. Click `Render`.
7. Confirm rendered widget data appears.
8. Confirm no raw JSON errors appear in the browser console.

### TC-032 - Report Query Catalog And Runtime Validation

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open the reporting or dashboard/report-query surface.
2. Create a report query catalog entry with a parameter schema.
3. Execute or render a widget that uses valid parameters.
4. Confirm data is returned.
5. Execute or render with invalid parameter values.
6. Confirm the backend rejects invalid input with a clear error.

### TC-033 - Reporting Snapshots And Rollups

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open the reporting/API tool surface or use `docs/http/trasck-api-examples.http`.
2. Run project or workspace reporting snapshot execution.
3. Backfill a small date range.
4. Run rollup materialization.
5. Read project snapshot series.
6. Confirm raw and rollup values are present.
7. Confirm destructive pruning is not available unless explicitly implemented later.

### TC-034 - Program Portfolio

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Programs`.
2. In `Program Portfolio`, create a program.
3. Confirm the program appears.
4. Open or select the program detail.
5. Edit roadmap or report config.
6. Save and reload.
7. In `Program Projects`, assign the current project.
8. Click `Load summary`.
9. Confirm program dashboard summary includes the project.
10. Remove the project and archive the program.

## Notifications, Automation, Webhooks, Email, And Workers

### TC-035 - Notification Preferences And Defaults

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Automation`.
2. In `Notification Preferences`, save a current-user preference.
3. Save a workspace default notification policy.
4. Trigger a notification by creating a work item update or automation action.
5. Load current-user notifications.
6. Mark a notification read.
7. Confirm read state persists after reload.

### TC-036 - Automation Rule Execution

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Automation`.
2. In `Automation Rule`, create a rule with a work item trigger.
3. In `Conditions And Actions`, add at least one condition.
4. Add at least one action, such as creating a notification.
5. In `Execution`, click `Queue rule`.
6. Run queued jobs.
7. Confirm job logs show success.
8. Confirm the expected action occurred.

### TC-037 - Worker Settings, Runs, And Health

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Automation`.
2. In worker settings, enable or disable scheduled automation as needed.
3. Save worker settings.
4. Run a manual worker process for automation or import review exports.
5. Click `Load`.
6. Confirm worker run rows appear.
7. Confirm worker health rows update.
8. Filter worker run/health rows by worker type.

### TC-038 - Webhook Delivery And Local Receiver

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Start a local HTTP receiver or use the Java Playwright local receiver if available.
2. Confirm backend outbound URL allowlist permits the receiver host and port.
3. Click `Automation`.
4. In `Webhooks`, create a webhook pointing at the receiver.
5. Trigger a domain event with an automation rule or work item change.
6. Process webhook deliveries.
7. Confirm the receiver gets a request.
8. Confirm request headers include Trasck webhook ID, delivery ID, timestamp, key ID, and signature.
9. Confirm delivery status is `delivered`.

### TC-039 - Webhook Secret Rotation Overlap

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open an existing webhook detail route.
2. Rotate or update the webhook secret.
3. Confirm previous key metadata and expiry are shown.
4. Queue a delivery created before rotation if possible.
5. Process the old delivery.
6. Confirm it signs with the previous key during overlap.
7. Queue a new delivery.
8. Confirm it signs with the current key.

### TC-040 - Maildev Email Delivery

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Open `http://localhost:1080`.
2. Confirm Maildev is running.
3. Click `Automation`.
4. Configure workspace email provider as Maildev.
5. Create an automation action that sends email.
6. Trigger the rule.
7. Process email deliveries in dry-run or real Maildev mode.
8. Confirm email delivery row status updates.
9. In Maildev, confirm the email appears when real sending is enabled.
10. Retry and cancel a delivery if controls are available.

### TC-041 - Worker Run Retention Export And Prune

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Automation`.
2. Load worker runs.
3. Set filters for worker type, trigger type, status, or date range.
4. Click the worker retention export control.
5. Confirm an export job is created.
6. Download the export artifact.
7. Confirm it contains only filtered rows.
8. Click prune with the same filters.
9. Confirm eligible rows are removed and unrelated worker rows remain.

## Imports

### TC-042 - Import Workspace Settings And Sample Job

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Imports`.
2. In `Sample Walkthrough`, load import settings.
3. Enable sample jobs for the workspace.
4. Click `Save setting`.
5. Load the sample catalog.
6. Select a CSV, Jira, or Rally sample.
7. Click `Create sample job`.
8. Confirm a job is created.
9. Confirm sample job creation is blocked if the workspace setting is disabled.

### TC-043 - Manual CSV Import Parse

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Imports`.
2. In `Import Job`, create a CSV job.
3. In `Parse`, paste a small CSV payload.
4. Click `Parse`.
5. Confirm import records are created.
6. Click `Load` in `Lifecycle`.
7. Confirm job status and counts are correct.
8. Confirm record endpoints return an empty list for a created job with no parsed records.

### TC-044 - Import Record Edit, Versions, And Diffs

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Select a parsed import job.
2. In `Import Records`, click `Apply filters`.
3. Select a record.
4. Edit the source payload or mapped field.
5. Save the record.
6. Load record version history.
7. Load field-level diffs.
8. Confirm the original and edited values are shown.
9. Load job-level diff export.
10. Confirm grouped diffs are understandable.

### TC-045 - Transform Preset Versions And Retarget

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Imports`.
2. Create a transform preset.
3. Edit the preset to create a new version if a detail route is available.
4. Click `Load versions`.
5. Confirm immutable version rows appear.
6. Select a historical version.
7. Click `Clone`.
8. Confirm a new standalone preset is created.
9. Select mapping templates for retargeting.
10. Click `Preview`.
11. Confirm affected templates are listed.
12. Click `Apply`.
13. Confirm selected templates now point to the cloned preset.

### TC-046 - Mapping Template And Mapping Rules

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Imports`.
2. In `Mapping Template`, select the current project and import source type.
3. Select a transform preset.
4. Enter field mapping and defaults.
5. Click `Create template`.
6. Open template detail.
7. Add a value lookup rule.
8. Add a work item type translation.
9. Add a status translation.
10. Save and reload.
11. Confirm rules appear and invalid rules show clear errors.

### TC-047 - Materialization And Snapshot Rerun

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Select a parsed import job.
2. Select a mapping template.
3. Click `Materialize`.
4. Confirm work items are created or updated.
5. Review materialization run counts for created, updated, skipped, and conflicted records.
6. Select the materialization run ID.
7. Run exact rerun.
8. Confirm snapshot settings are reused.
9. Run modified rerun with an explicit update-existing override.
10. Confirm counts and audit rows reflect the override.

### TC-048 - Conflict Resolution Single Selected And Filtered

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create or import records that produce conflicts.
2. In `Import Records`, filter to open conflicts.
3. Select one conflict record.
4. Resolve it with a single-record action.
5. Confirm status changes.
6. Select multiple open conflict records.
7. Click `Resolve selected`.
8. Confirm only selected records change.
9. Click `Preview filtered`.
10. Confirm matched count and batch cap appear.
11. Enter the exact confirmation text if required.
12. Click `Resolve filtered`.
13. Confirm the matched set changes only when expected count matches.

### TC-049 - Queued Conflict Resolution Job

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create a filtered conflict set above or near the synchronous batch cap.
2. Click `Preview filtered`.
3. Click `Queue filtered`.
4. Confirm a conflict-resolution job ID appears.
5. Click `Run job`.
6. Confirm job status moves to completed or failed with counts.
7. Retry a failed job if possible.
8. Cancel a queued job if possible.
9. Process queued jobs from the workspace import ops audit panel.

### TC-050 - Import Review Export Artifacts

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Imports`.
2. Apply a table filter for conflict jobs, export artifacts, or completion metrics.
3. Click the CSV/export artifact control.
4. Confirm a queued export job appears.
5. Click `Process review exports`.
6. Confirm export job status changes to completed.
7. Download the artifact.
8. Confirm downloaded CSV or JSON matches the filtered table.
9. Confirm secrets and internal storage keys are not present.

### TC-051 - Complete Import With Open Conflicts

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Select an import job with open conflicts.
2. Attempt to complete without open-conflict acceptance.
3. Confirm the backend rejects completion.
4. Enter `COMPLETE WITH OPEN CONFLICTS` in the confirmation field if present.
5. Enter an audit reason.
6. Complete the import.
7. Confirm open-conflict completion fields are visible on the job.
8. Load import completion metrics.
9. Confirm project/workspace dashboard import metrics reflect the accepted conflicts.

## AI Agents

### TC-052 - Simulated Agent Lifecycle

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Agents`.
2. Click `Load`.
3. Create a simulated provider.
4. Create an agent profile for the simulated provider.
5. Create or select a repository connection.
6. Select a work item.
7. Enter instructions.
8. Click `Assign`.
9. Confirm the agent task appears and status is running or review requested.
10. Click `Load` for the task.
11. Request changes.
12. Add a human message.
13. Retry or cancel if applicable.
14. Accept a review-requested result.
15. Confirm work item activity and comments reflect the agent lifecycle.

### TC-053 - Generic Worker Protocol

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create a `generic_worker` provider.
2. Create a `worker_token` credential with `workerId`.
3. Create an agent profile.
4. Assign a work item to the worker agent.
5. Use the HTTP examples or Java Playwright helper to claim a task with the worker token.
6. Send heartbeat.
7. Send progress log.
8. Send an agent message.
9. Add an artifact.
10. Complete through callback or worker protocol.
11. Confirm the task reaches review requested.
12. Confirm bad worker tokens are rejected and logged as auth failures.

### TC-054 - Codex Full-Capability CLI Worker

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Confirm the backend was started with `TRASCK_AGENTS_CLI_WORKER_ENABLED=true`.
2. Confirm `TRASCK_CODEX_AGENT_WORKDIR` points to a disposable checkout.
3. Confirm `codex` CLI auth works in a terminal on the backend host.
4. Click `Agents`.
5. Click `Codex preset`.
6. Click `Create provider`.
7. In `Credentials`, select the Codex provider.
8. Save a `codex_api_key` or `codex_cli_token` credential with the correct environment variable metadata.
9. Create a Codex agent profile.
10. Select a work item and repository connection.
11. Enter a small, safe implementation instruction.
12. Click `Assign`.
13. Wait for status to move to review requested or failed.
14. Click `Load` for the task.
15. Confirm task events include `cli_worker_started` and either `cli_worker_completed` or `cli_worker_failed`.
16. Inspect the disposable checkout for Codex changes.
17. Confirm Trasck captured a redacted output artifact and message.

### TC-055 - Claude Code Higher-Permission CLI Worker

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Confirm the backend was started with `TRASCK_AGENTS_CLI_WORKER_ENABLED=true`.
2. Confirm `TRASCK_CLAUDE_CODE_AGENT_WORKDIR` points to a disposable checkout.
3. Confirm `claude` CLI auth works in a terminal on the backend host.
4. Click `Agents`.
5. Click `Claude preset`.
6. Click `Create provider`.
7. In `Credentials`, select the Claude provider.
8. Save an `anthropic_api_key` or `claude_cli_token` credential with the correct environment variable metadata.
9. Create a Claude Code agent profile.
10. Select a work item and repository connection.
11. Enter a small, safe implementation instruction.
12. Click `Assign`.
13. Wait for status to move to review requested or failed.
14. Click `Load` for the task.
15. Confirm task events include `cli_worker_started` and either `cli_worker_completed` or `cli_worker_failed`.
16. Inspect the disposable checkout for Claude changes.
17. Confirm Trasck captured a redacted output artifact and message.

### TC-056 - Agent Credentials And Callback Keys

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Agents`.
2. Select an existing provider.
3. Click `Load credentials`.
4. Confirm raw secret values are not displayed.
5. Create a new provider credential.
6. Confirm the returned row does not contain the raw secret.
7. Click `Re-encrypt`.
8. Confirm credentials still list correctly.
9. Click `Rotate callback key`.
10. Confirm provider callback key metadata changes but private key material is not shown.
11. Deactivate a non-callback credential.
12. Confirm deactivated credentials cannot be used.

### TC-057 - Dispatch Attempts Admin Controls

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Click `Agents`.
2. Click `Load`.
3. Use `Attempt` and `Status` filters.
4. Click `Attempts`.
5. Confirm dispatch, retry, or cancel rows appear.
6. Click `Export attempts`.
7. Confirm an export job response appears in `Runtime Preview`.
8. Download the export artifact from the export job surface.
9. Confirm payloads are redacted.
10. Set retention days.
11. Click `Prune attempts`.
12. Confirm eligible rows are pruned only after export-before-prune behavior is honored.

### TC-058 - CLI Run Artifact Admin Controls

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Complete TC-054 or TC-055 so at least one CLI run directory exists.
2. Click `Agents`.
3. In `CLI Run Artifacts`, click `Load runs`.
4. Confirm the table shows task ID, provider type, status, file count, size, updated time, and artifact flags.
5. Click the download icon for a completed or review-requested run.
6. Confirm a ZIP file downloads.
7. Open the ZIP locally.
8. Confirm it contains `prompt.md`, `task.json`, and `output.log` when available.
9. Confirm the ZIP does not contain raw provider credentials.
10. Click the delete icon for one run.
11. Click `Load runs`.
12. Confirm that run no longer appears.
13. Set `Retention days` to `0` or another test value.
14. Click `Prune runs`.
15. Confirm old non-active runs are deleted and active running tasks are not deleted.

### TC-059 - Agent Permission Denial

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create or select a read-only/viewer role.
2. Create an agent profile using that role.
3. Assign a work item to the read-only agent.
4. Attempt to complete the task with a callback that creates a comment or artifact.
5. Confirm the callback is rejected if the agent lacks required permissions.
6. Confirm no unauthorized comment, artifact, or status change is written.
7. Confirm security or audit telemetry records the denial where expected.

## Security And Negative Tests

### TC-060 - Anonymous Public Read Boundaries

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Enable workspace anonymous read.
2. Set project visibility to public.
3. Create one public-safe work item and one private work item.
4. Add one public-safe comment and one private comment.
5. Upload an attachment intended for public download.
6. Open the public preview route in an incognito browser.
7. Confirm only public-safe project/work item/comment/attachment data appears.
8. Confirm user IDs, private comments, internal storage keys, and edit metadata are not shown.
9. Download the public attachment through the signed URL.
10. Disable workspace anonymous read.
11. Refresh the incognito preview and confirm access is denied.

### TC-061 - Permission Denial With Lower-Privilege User

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create a lower-privilege user with viewer-style permissions.
2. Log out.
3. Log in as that user.
4. Attempt to open admin pages such as `System`, `Workspace Settings`, and `Agents`.
5. Confirm restricted actions are hidden or rejected.
6. Attempt to create/update/delete a work item if the role lacks those permissions.
7. Confirm the request is denied and data is unchanged.
8. Log back in as admin.

### TC-062 - Secret Redaction Across UI And API

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Create provider credentials, webhook secret, SMTP password, and service token.
2. Load provider, webhook, email provider, token, audit, activity, and export views.
3. Confirm raw secrets never appear after initial token one-time display.
4. Export dispatch attempts and audit entries.
5. Download the exports.
6. Search downloaded files for known secret strings.
7. Confirm no raw secret appears.
8. Check browser console and network previews for unmasked secret values.

### TC-063 - Outbound URL Policy

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Configure a webhook or generic worker callback URL pointing to localhost or a private network address that is not allowlisted.
2. Trigger delivery.
3. Confirm the backend rejects the outbound URL.
4. Add a safe explicit allowlist entry in local config if needed.
5. Retry with a permitted local receiver.
6. Confirm delivery works only after allowlisting.
7. Confirm cloud metadata IPs and link-local ranges remain blocked.

### TC-064 - Attachment Import Export Size And Type Limits

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. In Workspace Settings or Project Settings, set a small attachment max upload size.
2. Attempt to upload a file larger than the limit.
3. Confirm upload is rejected.
4. Attempt to upload a disallowed content type.
5. Confirm upload is rejected.
6. Set a small import max size.
7. Attempt to parse an oversized import payload.
8. Confirm import is rejected.
9. Set export limit values and confirm export requests respect them.

### TC-065 - Production-Like Security Startup Smoke

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Stop the local backend.
2. Start a production-like profile with known development defaults in a disposable terminal.
3. Confirm startup fails with clear validation errors.
4. Set required secure environment values in a local uncommitted `.env` or shell.
5. Start again.
6. Confirm startup proceeds only when secure values and Redis-backed throttling are configured.
7. Confirm Swagger/OpenAPI require system-admin access in the production-like profile.

## Playwright And Final Regression

### TC-066 - Java Playwright API Suite

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Ensure backend is running on `http://localhost:6100`.
2. Ensure test environment variables point to the login, workspace, and project IDs or enable disposable setup.
3. Open a terminal in `trasck-test`.
4. Run `mvn test -Dgroups=api`.
5. Confirm tests pass or skipped tests explain missing optional config.
6. Review `test-results` for API snippets.
7. Add defects to `MANUAL_TEST_NOTES.MD` for failures.

### TC-067 - Java Playwright Frontend Browser Suite

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Ensure backend and frontend are both running.
2. Open a terminal in `trasck-test`.
3. Run the frontend/browser Playwright group used by the repo.
4. Confirm browser tests pass or skipped tests explain missing optional config.
5. Review screenshots and traces under `test-results`.
6. Confirm there are no browser console errors.
7. Add defects to `MANUAL_TEST_NOTES.MD` for failures.

### TC-068 - Managed Production Security Group

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Confirm Docker is running.
2. Confirm `TRASCK_BACKEND_REPOSITORY` is set if the managed stack requires it.
3. Open a terminal in `trasck-test`.
4. Run `TRASCK_E2E_MANAGED_PROD_STACK=true mvn test -Dgroups=managed-stack`.
5. Confirm unsafe production defaults fail startup.
6. Confirm secure production-like settings allow startup.
7. Confirm OpenAPI/Swagger and admin gates behave as expected.

### TC-069 - Manual Bug Fix And Retest Loop

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. For every failed test case, add a defect entry to `MANUAL_TEST_NOTES.MD`.
2. Include exact steps to reproduce.
3. Include screenshots or logs when useful.
4. Fix one defect at a time.
5. Add or update backend/frontend/Playwright regression coverage for the defect.
6. Rerun the failed manual case.
7. Update this file's result block and the defect note.
8. Continue until no blocking defects remain.

### TC-070 - Manual Test Exit Checklist

Result: Pass / Fail / Blocked / Not Run

Notes:

Defect IDs:

Steps:

1. Confirm all critical and high manual test cases are pass or explicitly accepted.
2. Confirm all new defects have regression coverage or a documented reason they cannot yet be automated.
3. Confirm Codex and Claude Code agent runs were tested against disposable checkouts.
4. Confirm CLI run artifacts were downloaded, inspected, and cleaned up through the UI.
5. Confirm no raw secrets are visible in UI, logs, exports, downloaded artifacts, or browser storage.
6. Confirm `NEXT_STEPS.MD` reflects remaining work after the manual round.
7. Confirm `TECH_SPECS.MD` still matches implemented behavior.
