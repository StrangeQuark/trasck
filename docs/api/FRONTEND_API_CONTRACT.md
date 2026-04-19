# Frontend API Contract

This document is the frontend-facing companion to generated OpenAPI. The backend exposes OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`; this file captures frontend-oriented flow notes and response shapes that are easier to scan than the generated schema.

## Transport

- Base path: `/api/v1`
- JSON content type: `application/json`
- Auth: `Authorization: Bearer <jwt-or-api-token>` for API clients. Browser cookie auth is supported after login, but unsafe cookie-authenticated requests must send the CSRF header returned by `GET /api/v1/auth/csrf`.
- IDs are UUID strings unless an entity exposes a human key such as `workItem.key`.
- Timestamps are ISO-8601 strings with offset.
- Errors use Spring's standard error body for now; frontend should branch mainly on HTTP status.
- Generated OpenAPI: `GET /v3/api-docs`; Swagger UI: `GET /swagger-ui.html`.
- Generated frontend client: run `npm run generate:api` from `trasck-frontend`. By default it reads `http://localhost:6100/v3/api-docs` and writes `src/api/generated/openapi.json` plus `src/api/generated/trasckApi.ts`.

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
- Workspace/project configuration lists: teams, team memberships, project-team assignments, labels, boards, workflows, statuses, roles.
- Dashboard, dashboard widget, saved filter, saved view, report query catalog, repository connection, agent provider/profile/credential lists, including workspace/project/team scoped dashboard/filter/view/catalog lists.
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

## Common Flows

1. First setup: `POST /api/v1/setup`, then store IDs from `workspace`, `project`, `adminUser`, and `seedData`.
2. Login: `POST /api/v1/auth/login`, then use `accessToken` as a Bearer token for local frontend development.
3. Project work list: `GET /api/v1/projects/{projectId}/work-items?limit=50`, optionally add one typed custom-field filter, follow `nextCursor` for more pages, then `GET /api/v1/work-items/{workItemId}` for detail.
4. Work item detail tabs: comments, links, watchers, work logs, labels, attachments, activity, and reporting history all hang off the selected work item ID.
5. Dashboard builder: create a saved filter, create a governed report query catalog entry with optional `parametersSchema`, create a dashboard/widget, then render with `GET /api/v1/dashboards/{dashboardId}/render`.
6. Agent assignment: create provider/profile/repository connection, assign a work item with `POST /api/v1/work-items/{workItemId}/assign-agent`, then show task messages/artifacts/status until review or completion.
7. Audit retention: update policy, export candidates to storage, then prune. Pruning writes a stored export before deleting eligible audit rows. Admin export history uses `GET /api/v1/workspaces/{workspaceId}/export-jobs`, metadata uses `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}`, and artifact download uses `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}/download`.
8. Reporting snapshots: run or backfill raw snapshots, optionally update `snapshot-retention-policy`, run/backfill rollups, then read `GET /api/v1/reports/projects/{projectId}/snapshots` for raw `series` plus additive `rollupSeries`.

## Endpoint Coverage

- Setup: `POST /setup`
- Auth: login, current user, CSRF, personal tokens, workspace service tokens, invitations, direct user creation.
- Work items: project list/create, typed single custom-field list filter, create/update keyed `customFields`, screen required-field enforcement on create/update, detail/update/archive, assignment, rank, transition, team assignment, comments, links, watchers, work logs, labels, attachments.
- Teams/planning: team CRUD, memberships, project-team assignment, iteration CRUD, scope, commit, close, carryover.
- Reporting: work item histories, work-log summary, project/workspace/program dashboard summaries, snapshot run/backfill/reconcile, snapshot retention policy, rollup run/backfill, raw snapshots with `rollupSeries`, iteration reports.
- Dashboards/search: dashboard CRUD/render, widget CRUD, workspace/project/team dashboard lists, saved filter CRUD plus workspace/project/team lists, saved view CRUD plus workspace/project/team lists, report query catalog CRUD plus workspace/project/team lists.
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
