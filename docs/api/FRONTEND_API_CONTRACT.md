# Frontend API Contract

This document is the frontend-facing companion to generated OpenAPI. The backend exposes OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`; this file captures frontend-oriented flow notes and response shapes that are easier to scan than the generated schema.

## Transport

- Base path: `/api/v1`
- JSON content type: `application/json`
- Auth: `Authorization: Bearer <jwt-or-api-token>` for direct API clients. The browser frontend uses the HTTP-only auth cookie set by login and does not store access tokens in local storage. Unsafe cookie-authenticated requests must send the CSRF header returned by `GET /api/v1/auth/csrf`.
- IDs are UUID strings unless an entity exposes a human key such as `workItem.key`.
- Timestamps are ISO-8601 strings with offset.
- Errors use Spring's standard error body for now; frontend should branch mainly on HTTP status.
- Generated OpenAPI: `GET /v3/api-docs`; Swagger UI: `GET /swagger-ui.html`.
- Generated frontend client: run `npm run generate:api` from `trasck-frontend`. By default it reads `http://localhost:6100/v3/api-docs` and writes `src/api/generated/openapi.json` plus `src/api/generated/trasckApi.ts`.
- Frontend dev server: `http://localhost:8080`; backend default: `http://localhost:6100`. The frontend reads `VITE_TRASCK_API_BASE_URL`, then `VITE_API_URL`, then falls back to the backend default.
- UI components should call feature-specific services/hooks, not `TrasckApiClient.request` directly.

## List Behavior

High-volume endpoints return cursor pages:

- `GET /api/v1/projects/{projectId}/work-items`
- `GET /api/v1/work-items/{workItemId}/activity`
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/activity`
- `GET /api/v1/workspaces/{workspaceId}/activity`
- `GET /api/v1/workspaces/{workspaceId}/audit-log`
- `GET /api/v1/workspaces/{workspaceId}/export-jobs`

Cursor-page requests accept `limit` and `cursor`. Clients should request the next page with the prior response's `nextCursor` while `hasMore` is true.

Small configuration or scoped-detail endpoints still return arrays:

- Setup seed data, auth token lists, service-token lists.
- Workspace/project configuration lists: teams, team memberships, project-team assignments, labels, boards, board columns, board swimlanes, workflows, statuses, roles, field configurations, releases, release work items, roadmaps, and roadmap items.
- Dashboard, dashboard widget, saved filter, saved view, report query catalog, repository connection, agent provider/profile/credential lists, including workspace/project/team scoped dashboard/filter/view/catalog lists.
- Notification, notification preference, automation rule, automation condition, automation action, automation job/log, webhook/delivery, import job, and import record lists.
- Work item collaboration lists: comments, links, watchers, work logs, labels, attachments.
- Reporting history lists for one work item or one scoped report.

## TypeScript Shapes

```ts
export type UUID = string;
export type ISODate = string;
export type ISODateTime = string;
export type JsonObject = Record<string, unknown>;

export interface CursorPage<T> {
  items: T[];
  nextCursor?: string;
  hasMore: boolean;
  limit: number;
}

export interface SetupRequest {
  adminUser: { email: string; username: string; displayName: string; password: string };
  organization: { name: string; slug: string };
  workspace: { name: string; key: string; timezone?: string; locale?: string; anonymousReadEnabled?: boolean };
  project: { name: string; key: string; description?: string; visibility?: "private" | "public" };
}

export interface AuthSession {
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: ISODateTime;
  user: User;
}

export interface User {
  id: UUID;
  email: string;
  username: string;
  displayName: string;
  accountType: "human" | "service" | "agent";
  emailVerified: boolean;
  lastLoginAt?: ISODateTime;
}

export interface WorkItem {
  id: UUID;
  workspaceId: UUID;
  projectId: UUID;
  key: string;
  sequenceNumber: number;
  workspaceSequenceNumber: number;
  typeId: UUID;
  statusId: UUID;
  priorityId?: UUID;
  resolutionId?: UUID;
  reporterId?: UUID;
  assigneeId?: UUID;
  parentId?: UUID;
  teamId?: UUID;
  title: string;
  descriptionMarkdown?: string;
  descriptionDocument?: JsonObject;
  visibility: "inherited" | "private" | "public";
  estimatePoints?: number;
  estimateMinutes?: number;
  remainingMinutes?: number;
  rank: string;
  startDate?: ISODate;
  dueDate?: ISODate;
  resolvedAt?: ISODateTime;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
  deletedAt?: ISODateTime;
}

export interface ProjectWorkItemListQuery {
  limit?: number;
  cursor?: string;
  customFieldKey?: string;
  customFieldOperator?: "eq" | "ne" | "contains" | "not_contains" | "in" | "gt" | "gte" | "lt" | "lte" | "between";
  customFieldValue?: string;
  customFieldValueTo?: string;
}

export interface WorkItemCreateRequest {
  typeId?: UUID;
  typeKey?: string;
  parentId?: UUID;
  statusId?: UUID;
  statusKey?: string;
  priorityId?: UUID;
  priorityKey?: string;
  teamId?: UUID;
  assigneeId?: UUID;
  reporterId?: UUID;
  title: string;
  descriptionMarkdown?: string;
  descriptionDocument?: JsonObject;
  visibility?: WorkItem["visibility"];
  estimatePoints?: number;
  estimateMinutes?: number;
  remainingMinutes?: number;
  startDate?: ISODate;
  dueDate?: ISODate;
  customFields?: Record<string, unknown>;
}

export interface WorkItemUpdateRequest extends Partial<Omit<WorkItemCreateRequest, "statusId" | "statusKey">> {
  clearParent?: boolean;
  clearTeam?: boolean;
}

export interface Dashboard {
  id: UUID;
  workspaceId: UUID;
  ownerId: UUID;
  projectId?: UUID;
  teamId?: UUID;
  name: string;
  visibility: "private" | "team" | "project" | "workspace" | "public";
  layout: JsonObject;
  widgets: DashboardWidget[];
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface DashboardWidget {
  id: UUID;
  dashboardId: UUID;
  widgetType: string;
  title?: string;
  config: JsonObject;
  positionX: number;
  positionY: number;
  width: number;
  height: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface SavedFilter {
  id: UUID;
  workspaceId: UUID;
  ownerId: UUID;
  projectId?: UUID;
  teamId?: UUID;
  name: string;
  visibility: Dashboard["visibility"];
  query: JsonObject;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface SavedView {
  id: UUID;
  workspaceId: UUID;
  ownerId: UUID;
  projectId?: UUID;
  teamId?: UUID;
  name: string;
  viewType: string;
  visibility: Dashboard["visibility"];
  config: JsonObject;
}

export interface ReportQueryCatalogEntry {
  id: UUID;
  workspaceId: UUID;
  ownerId: UUID;
  projectId?: UUID;
  teamId?: UUID;
  queryKey: string;
  name: string;
  description?: string;
  queryType:
    | "project_dashboard_summary"
    | "workspace_dashboard_summary"
    | "portfolio_dashboard_summary"
    | "program_dashboard_summary"
    | "snapshot_series"
    | "iteration_report";
  queryConfig: JsonObject;
  parametersSchema: JsonObject;
  visibility: Dashboard["visibility"];
  enabled: boolean;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface ReportingSnapshotSeriesPoint {
  date: ISODate;
  metricKey: string;
  entityId?: UUID;
  label: string;
  value: number;
}

export interface ProjectReportingSnapshots {
  projectId: UUID;
  workspaceId: UUID;
  fromDate: ISODate;
  toDate: ISODate;
  cycleTimeRecords: unknown[];
  iterationSnapshots: unknown[];
  velocitySnapshots: unknown[];
  cumulativeFlowSnapshots: unknown[];
  series: ReportingSnapshotSeriesPoint[];
  rollupSeries: ReportingSnapshotSeriesPoint[];
}

export interface ReportingRetentionPolicy {
  id?: UUID;
  workspaceId: UUID;
  rawRetentionDays: number;
  weeklyRollupAfterDays: number;
  monthlyRollupAfterDays: number;
  archiveAfterDays: number;
  destructivePruningEnabled: boolean;
  createdById?: UUID;
  updatedById?: UUID;
  createdAt?: ISODateTime;
  updatedAt?: ISODateTime;
}

export interface ReportingRollupRun {
  workspaceId: UUID;
  archiveRunId: UUID;
  action: "rollup_run" | "rollup_backfill";
  granularity: "daily" | "weekly" | "monthly";
  fromDate: ISODate;
  toDate: ISODate;
  cycleTimeRollups: number;
  iterationRollups: number;
  velocityRollups: number;
  cumulativeFlowRollups: number;
  genericRollups: number;
  rawRowsPruned: number;
}

export interface AuditLogEntry {
  id: UUID;
  domainEventId: UUID;
  workspaceId: UUID;
  actorId?: UUID;
  action: string;
  targetType: string;
  targetId?: UUID;
  beforeValue?: unknown;
  afterValue?: unknown;
  ipAddress?: string;
  userAgent?: string;
  createdAt: ISODateTime;
}

export interface ActivityEvent {
  id: UUID;
  domainEventId: UUID;
  workspaceId: UUID;
  actorId?: UUID;
  entityType: "workspace" | "project" | "work_item" | string;
  entityId: UUID;
  eventType: string;
  metadata?: unknown;
  createdAt: ISODateTime;
}

export interface AuditRetentionExport {
  workspaceId: UUID;
  retentionEnabled: boolean;
  retentionDays?: number;
  cutoff?: ISODateTime;
  entriesEligible: number;
  exportJobId: UUID;
  fileAttachmentId: UUID;
  filename: string;
  storageKey: string;
  checksum: string;
  sizeBytes: number;
  entries: AuditLogEntry[];
}

export interface ExportJob {
  id: UUID;
  workspaceId: UUID;
  requestedById?: UUID;
  exportType: string;
  status: "queued" | "running" | "completed" | "failed" | string;
  fileAttachmentId?: UUID;
  filename?: string;
  contentType?: string;
  sizeBytes?: number;
  checksum?: string;
  startedAt: ISODateTime;
  finishedAt?: ISODateTime;
}

export interface FieldConfiguration {
  id: UUID;
  workspaceId: UUID;
  customFieldId: UUID;
  projectId?: UUID;
  workItemTypeId?: UUID;
  required: boolean;
  hidden: boolean;
  defaultValue?: unknown;
  validationConfig?: unknown;
}

export interface BoardColumn {
  id: UUID;
  boardId: UUID;
  name: string;
  statusIds: unknown;
  position: number;
  wipLimit?: number;
  doneColumn: boolean;
}

export interface BoardSwimlane {
  id: UUID;
  boardId: UUID;
  name: string;
  swimlaneType: string;
  query: unknown;
  position: number;
  enabled: boolean;
}

export interface Board {
  id: UUID;
  workspaceId: UUID;
  projectId: UUID;
  teamId?: UUID;
  name: string;
  type: "scrum" | "kanban" | "portfolio" | string;
  filterConfig: unknown;
  active: boolean;
  version: number;
  columns: BoardColumn[];
  swimlanes: BoardSwimlane[];
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface Release {
  id: UUID;
  projectId: UUID;
  name: string;
  version: string;
  startDate?: ISODate;
  releaseDate?: ISODate;
  status: "planned" | "in_progress" | "released" | "archived" | string;
  description?: string;
}

export interface ReleaseWorkItem {
  releaseId: UUID;
  workItemId: UUID;
  addedById?: UUID;
  addedAt: ISODateTime;
}

export interface RoadmapItem {
  id: UUID;
  roadmapId: UUID;
  workItemId: UUID;
  startDate?: ISODate;
  endDate?: ISODate;
  position: number;
  displayConfig: unknown;
}

export interface Roadmap {
  id: UUID;
  workspaceId: UUID;
  projectId?: UUID;
  name: string;
  config: unknown;
  ownerId: UUID;
  visibility: Dashboard["visibility"];
  items: RoadmapItem[];
}

export interface Notification {
  id: UUID;
  userId: UUID;
  actorId?: UUID;
  workspaceId: UUID;
  type: string;
  title: string;
  body?: string;
  targetType?: string;
  targetId?: UUID;
  readAt?: ISODateTime;
  createdAt: ISODateTime;
}

export interface NotificationPreference {
  id: UUID;
  userId: UUID;
  workspaceId: UUID;
  channel: "in_app" | "email" | "webhook" | string;
  eventType: string;
  enabled: boolean;
  config: unknown;
}

export interface AutomationCondition {
  id: UUID;
  ruleId: UUID;
  conditionType: string;
  config: unknown;
  position: number;
}

export interface AutomationAction {
  id: UUID;
  ruleId: UUID;
  actionType: "create_notification" | "notification" | "email" | "webhook" | string;
  executionMode: "sync" | "async" | string;
  config: unknown;
  position: number;
}

export interface AutomationRule {
  id: UUID;
  workspaceId: UUID;
  projectId?: UUID;
  name: string;
  triggerType: string;
  triggerConfig: unknown;
  enabled: boolean;
  conditions: AutomationCondition[];
  actions: AutomationAction[];
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface AutomationExecutionLog {
  id: UUID;
  jobId: UUID;
  actionId?: UUID;
  status: "succeeded" | "failed" | "skipped" | string;
  message: string;
  metadata: unknown;
  createdAt: ISODateTime;
}

export interface AutomationExecutionJob {
  id: UUID;
  ruleId: UUID;
  workspaceId: UUID;
  sourceEntityType?: string;
  sourceEntityId?: UUID;
  status: "queued" | "running" | "succeeded" | "failed" | string;
  payload: unknown;
  attempts: number;
  nextAttemptAt?: ISODateTime;
  startedAt?: ISODateTime;
  completedAt?: ISODateTime;
  failedAt?: ISODateTime;
  lastError?: string;
  createdAt: ISODateTime;
  logs: AutomationExecutionLog[];
}

export interface Webhook {
  id: UUID;
  workspaceId: UUID;
  name: string;
  url: string;
  secretConfigured: boolean;
  eventTypes: unknown;
  enabled: boolean;
}

export interface WebhookDelivery {
  id: UUID;
  webhookId: UUID;
  eventType: string;
  payload: unknown;
  status: "queued" | "delivered" | "failed" | "dead_letter" | string;
  responseCode?: number;
  responseBody?: string;
  attemptCount: number;
  nextRetryAt?: ISODateTime;
  createdAt: ISODateTime;
}

export interface ImportJobRecord {
  id: UUID;
  importJobId: UUID;
  sourceType: string;
  sourceId: string;
  targetType?: string;
  targetId?: UUID;
  status: "pending" | "imported" | "failed" | "skipped" | string;
  errorMessage?: string;
  rawPayload?: unknown;
}

export interface ImportJob {
  id: UUID;
  workspaceId: UUID;
  requestedById?: UUID;
  provider: "jira" | "rally" | "csv" | string;
  status: "queued" | "running" | "completed" | "failed" | "canceled" | string;
  config: unknown;
  startedAt?: ISODateTime;
  finishedAt?: ISODateTime;
  records: ImportJobRecord[];
}

export interface AgentTask {
  id: UUID;
  workspaceId: UUID;
  workItemId: UUID;
  agentProfileId: UUID;
  providerId: UUID;
  requestedById: UUID;
  status: "queued" | "running" | "waiting_for_input" | "review_requested" | "completed" | "failed" | "canceled";
  dispatchMode: string;
  externalTaskId?: string;
  contextSnapshot: JsonObject;
  requestPayload: JsonObject;
  resultPayload?: JsonObject;
  queuedAt: ISODateTime;
  startedAt?: ISODateTime;
  completedAt?: ISODateTime;
  failedAt?: ISODateTime;
  canceledAt?: ISODateTime;
  events: unknown[];
  messages: unknown[];
  artifacts: unknown[];
  repositories: unknown[];
  callbackHeaderName?: string;
  callbackToken?: string;
}
```

Browser UI code should use `AuthSession.user` and the auth cookie. The returned `accessToken` remains part of the API response for direct API tools and non-browser clients, not as browser session storage.

## Common Flows

1. First setup: `POST /api/v1/setup`, then store IDs from `workspace`, `project`, `adminUser`, and `seedData`.
2. Login: `POST /api/v1/auth/login`; browser sessions should prefer the HTTP-only cookie plus `GET /api/v1/auth/csrf` for unsafe requests. The returned `accessToken` remains available for API tools and development overrides.
3. Project work list: `GET /api/v1/projects/{projectId}/work-items?limit=50`, optionally add one typed custom-field filter, follow `nextCursor` for more pages, then `GET /api/v1/work-items/{workItemId}` for detail.
4. Work item detail tabs: comments, links, watchers, work logs, labels, attachments, activity, and reporting history all hang off the selected work item ID.
5. Product configuration: create custom fields/contexts, add field configurations for project/type overrides, create screens/fields/assignments, then create/update work items to exercise required-field enforcement.
6. Planning configuration: list/create boards with columns and swimlanes, create releases and release scope, create project/workspace roadmaps, and add roadmap items linked to work items.
7. Automation configuration: create a notification preference, configure webhooks, create automation rules/actions, run `POST /api/v1/automation-rules/{ruleId}/execute`, then read automation jobs/logs, current-user notifications, webhook delivery rows, email delivery rows, worker runs, worker health, and scheduled worker settings. Webhook/email deliveries can be inspected, retried, cancelled, and processed manually; scheduled workers are controlled per workspace.
8. Import review/materialization: create an import job, parse CSV/Jira/Rally content into records, create an import mapping template with optional transformation config, add value lookups or type/status translations where needed, materialize records into work items, and move the job through start/complete/fail/cancel states while showing records on the job detail screen.
9. Dashboard builder: create a saved filter, optionally execute it with `GET /api/v1/saved-filters/{savedFilterId}/work-items`, create a governed report query catalog entry with optional `parametersSchema`, create a dashboard/widget, then render with `GET /api/v1/dashboards/{dashboardId}/render`.
10. Agent assignment: create provider/profile/repository connection, assign a work item with `POST /api/v1/work-items/{workItemId}/assign-agent`, then show task messages/artifacts/status until review or completion.
11. Audit retention: update policy, export candidates to storage, then prune. Pruning writes a stored export before deleting eligible audit rows. Admin export history uses `GET /api/v1/workspaces/{workspaceId}/export-jobs`, metadata uses `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}`, and artifact download uses `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}/download`.
12. Reporting snapshots: run or backfill raw snapshots, optionally update `snapshot-retention-policy`, run/backfill rollups, then read `GET /api/v1/reports/projects/{projectId}/snapshots` for raw `series` plus additive `rollupSeries`.

## Endpoint Coverage

- Setup: `POST /setup`
- Auth: login, current user, CSRF, personal tokens, workspace service tokens, invitations, direct user creation.
- Work items: project list/create, typed single custom-field list filter, create/update keyed `customFields`, screen required-field enforcement on create/update, targeted required-field checks on assignee/team commands, detail/update/archive, assignment, rank, transition, team assignment, comments, links, watchers, work logs, labels, attachments.
- Product configuration: custom field/context/value CRUD, field configuration CRUD with project/type overrides, screen/field/assignment CRUD.
- Teams/planning: team CRUD, memberships, project-team assignment, board/column/swimlane CRUD, iteration CRUD, scope, commit, close, carryover, release CRUD/scope, roadmap CRUD/items.
- Reporting: work item histories, work-log summary, project/workspace/program dashboard summaries, snapshot run/backfill/reconcile, snapshot retention policy, rollup run/backfill, raw snapshots with `rollupSeries`, iteration reports.
- Dashboards/search: dashboard CRUD/render, widget CRUD, workspace/project/team dashboard lists, saved filter CRUD plus workspace/project/team lists and cursor-paged work item execution, saved view CRUD plus workspace/project/team lists, report query catalog CRUD plus workspace/project/team lists.
- Notifications/automation/import: current-user notifications, notification preferences, automation rule/condition/action CRUD, manual rule execution with job logs, worker settings, worker run/health reads, webhook CRUD plus queued delivery records/retry/cancel/process APIs, Maildev-backed email delivery records/retry/cancel/process APIs, import job lifecycle, parser, mapping-template, value lookup, type/status translation, materialization, and record APIs.
- Audit/admin: cursor-page audit log, audit retention policy/export/prune, cursor-page export jobs, export metadata/download, domain event replay.
- Agents: providers, credentials, callback keys, profiles, repository connections, assignment, worker dispatch, worker protocol, callbacks, task messages/artifacts/review actions.

## Custom Field Search

`GET /api/v1/projects/{projectId}/work-items` accepts one custom-field predicate at a time:

- `customFieldKey`: custom field key for a searchable field that applies to the project.
- `customFieldOperator`: defaults to `eq`; use one of the operators below.
- `customFieldValue`: required when filtering. For `in`, send a comma-separated string.
- `customFieldValueTo`: required only with `between`.

Supported operator groups:

| Field Types | Operators |
|---|---|
| `text`, `textarea`, `single_select`, `user`, `url` | `eq`, `ne`, `contains`, `not_contains`, `in` |
| `number`, `integer`, `date`, `datetime` | `eq`, `ne`, `in`, `gt`, `gte`, `lt`, `lte`, `between` |
| `boolean` | `eq`, `ne` |
| `multi_select` | `contains`, `not_contains`, `in` |
| `json` | `eq`, `ne`; `customFieldValue` must be valid JSON text |

## Saved Filter Execution

`GET /api/v1/saved-filters/{savedFilterId}/work-items?limit=&cursor=` executes a saved work item query and returns the standard cursor-page envelope of `WorkItemResponse` items.

The query JSON stored on the saved filter supports:

- Scope: top-level `projectId`, `projectIds`, and optional `teamId`. Project-visible saved filters are constrained to their saved `projectId`; team-visible filters are constrained to their saved `teamId`.
- Predicates: use `where` for a predicate/group or `filters` as an implicit `and` group. Boolean groups use `{ "op": "and" | "or", "conditions": [...] }`.
- System fields: `{ "field": "title", "operator": "contains", "value": "api" }`.
- Custom fields: `{ "customFieldKey": "customer", "operator": "eq", "value": "Acme" }` or `customFieldId`.
- Sort: `workspaceSequenceNumber`, `createdAt`, `updatedAt`, `dueDate`, and `priority` for work item scopes. `rank` is available when the saved filter resolves to one project.

```json
{
  "entityType": "work_item",
  "projectId": "00000000-0000-0000-0000-000000000000",
  "where": {
    "op": "and",
    "conditions": [
      { "field": "typeKey", "operator": "eq", "value": "story" },
      { "field": "title", "operator": "contains", "value": "checkout" },
      { "customFieldKey": "customer", "operator": "eq", "value": "Acme" }
    ]
  },
  "sort": [{ "field": "dueDate", "direction": "asc" }]
}
```

## Report Query Parameters

`parametersSchema` is enforced when a dashboard widget resolves a report query catalog entry. Supported types are `string`, `uuid`, `integer`, `number`, `boolean`, `date`, `datetime`, `array`, and `object`.

```json
{
  "type": "object",
  "required": ["projectId", "teamId"],
  "additionalProperties": false,
  "properties": {
    "projectId": { "type": "uuid" },
    "teamId": { "type": "uuid" },
    "from": { "type": "datetime" },
    "to": { "type": "datetime" }
  }
}
```
