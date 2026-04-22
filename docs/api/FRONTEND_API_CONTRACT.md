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
- Import sample job endpoints are workspace-admin-only. They require the workspace import setting `sampleJobsEnabled=true`. Local/default runtime profiles can enable the workspace setting directly; production-like `prod`, `production`, `staging`, and `hosted` profiles also require `TRASCK_IMPORT_SAMPLE_JOBS_ENABLED=true`.

## List Behavior

High-volume endpoints return cursor pages:

- `GET /api/v1/projects/{projectId}/work-items`
- `GET /api/v1/work-items/{workItemId}/activity`
- `GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/activity`
- `GET /api/v1/workspaces/{workspaceId}/activity`
- `GET /api/v1/workspaces/{workspaceId}/audit-log`
- `GET /api/v1/workspaces/{workspaceId}/export-jobs`
- `GET /api/v1/public/projects/{projectId}/work-items`

Cursor-page requests accept `limit` and `cursor`. Clients should request the next page with the prior response's `nextCursor` while `hasMore` is true.

Small configuration or scoped-detail endpoints still return arrays:

- Setup seed data, auth token lists, service-token lists.
- Workspace/project configuration lists: teams, team memberships, project-team assignments, labels including workspace label cleanup, boards, board columns, board swimlanes, workflows, statuses, roles, field configurations, releases, release work items, roadmaps, and roadmap items.
- Program, program-project assignment, dashboard, dashboard widget, saved filter, saved view, report query catalog, repository connection list/create/deactivate, agent provider/profile/credential lists and credential maintenance actions, including workspace/project/team scoped dashboard/filter/view/catalog lists.
- Notification, notification preference, automation rule, automation condition, automation action, automation job/log, webhook/delivery, import job, import sample, import transform preset/version, import mapping template, import record, and import conflict-resolution job lists.
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
  project: { name: string; key: string; description?: string; visibility?: "private" | "workspace" | "public" };
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

export interface PublicProject {
  id: UUID;
  workspaceId: UUID;
  name: string;
  key: string;
  description?: string;
  visibility: "public";
}

export interface PublicWorkItem {
  id: UUID;
  projectId: UUID;
  key: string;
  typeId: UUID;
  statusId: UUID;
  priorityId?: UUID;
  parentId?: UUID;
  teamId?: UUID;
  title: string;
  descriptionMarkdown?: string;
  descriptionDocument?: JsonObject;
  visibility: "inherited" | "public";
  estimatePoints?: number;
  startDate?: ISODate;
  dueDate?: ISODate;
  resolvedAt?: ISODateTime;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
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

export interface WorkItemComment {
  id: UUID;
  workItemId: UUID;
  authorId: UUID;
  bodyMarkdown: string;
  bodyDocument?: unknown;
  visibility: "workspace" | "public" | "private";
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface WorkItemLink {
  id: UUID;
  sourceWorkItemId: UUID;
  targetWorkItemId: UUID;
  linkType: "relates_to" | "blocks" | "blocked_by" | "duplicates" | "is_duplicated_by" | string;
  createdById: UUID;
  createdAt: ISODateTime;
}

export interface WorkItemWatcher {
  workItemId: UUID;
  userId: UUID;
  createdAt: ISODateTime;
}

export interface WorkLog {
  id: UUID;
  workItemId: UUID;
  userId: UUID;
  minutesSpent: number;
  workDate: ISODate;
  startedAt?: ISODateTime;
  descriptionMarkdown?: string;
  descriptionDocument?: unknown;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface Label {
  id: UUID;
  workspaceId: UUID;
  name: string;
  color?: string;
  createdAt: ISODateTime;
}

export interface WorkItemAttachment {
  id: UUID;
  workspaceId: UUID;
  storageConfigId: UUID;
  uploaderId: UUID;
  filename: string;
  contentType?: string;
  storageKey: string;
  sizeBytes: number;
  checksum?: string;
  visibility: "restricted" | "public";
  createdAt: ISODateTime;
}

// Frontend work item detail controls should use these collaboration endpoints through workItemsService.
// Attachment upload uses multipart/form-data with file, optional checksum, and visibility.
// The current UI supports both drag/drop and a standard file picker.

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

export interface ProgramProject {
  programId: UUID;
  projectId: UUID;
  position: number;
  createdAt: ISODateTime;
}

export interface Program {
  id: UUID;
  workspaceId: UUID;
  name: string;
  description?: string;
  status: "active" | "archived";
  roadmapConfig: JsonObject;
  reportConfig: JsonObject;
  projects: ProgramProject[];
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

export interface ImportCompletionMetrics {
  completedJobs: number;
  completedWithOpenConflicts: number;
  acceptedOpenConflictCount: number;
  lastOpenConflictCompletedAt?: ISODateTime;
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
  savedFilterId?: UUID;
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

export interface BoardWorkItemTransitionRequest {
  transitionKey?: string;
  targetColumnId?: UUID;
  targetStatusId?: UUID;
}

export interface BoardWorkItemMoveRequest {
  transitionKey?: string;
  targetColumnId?: UUID;
  targetStatusId?: UUID;
  previousWorkItemId?: UUID;
  nextWorkItemId?: UUID;
}

// Browser drag/drop should send targetColumnId plus previousWorkItemId/nextWorkItemId
// from the target column when the user drops before or after a specific card.

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

export interface NotificationCreateRequest {
  userId: UUID;
  type?: string;
  title: string;
  body?: string;
  targetType?: string;
  targetId?: UUID;
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

export interface AutomationWorkerSettings {
  workspaceId: UUID;
  automationJobsEnabled: boolean;
  webhookDeliveriesEnabled: boolean;
  emailDeliveriesEnabled: boolean;
  importConflictResolutionEnabled: boolean;
  importReviewExportsEnabled: boolean;
  automationLimit: number;
  webhookLimit: number;
  emailLimit: number;
  importConflictResolutionLimit: number;
  importReviewExportLimit: number;
  webhookMaxAttempts: number;
  emailMaxAttempts: number;
  webhookDryRun: boolean;
  emailDryRun: boolean;
  workerRunRetentionEnabled: boolean;
  workerRunRetentionDays: number;
  workerRunExportBeforePrune: boolean;
  workerRunPruningAutomaticEnabled: boolean;
  workerRunPruningIntervalMinutes: number;
  workerRunPruningWindowStart?: string;
  workerRunPruningWindowEnd?: string;
  workerRunPruningLastStartedAt?: ISODateTime;
  workerRunPruningLastFinishedAt?: ISODateTime;
  agentDispatchAttemptRetentionEnabled: boolean;
  agentDispatchAttemptRetentionDays: number;
  agentDispatchAttemptExportBeforePrune: boolean;
  agentDispatchAttemptPruningAutomaticEnabled: boolean;
  agentDispatchAttemptPruningIntervalMinutes: number;
  agentDispatchAttemptPruningWindowStart?: string;
  agentDispatchAttemptPruningWindowEnd?: string;
  agentDispatchAttemptPruningLastStartedAt?: ISODateTime;
  agentDispatchAttemptPruningLastFinishedAt?: ISODateTime;
  updatedAt: ISODateTime;
}

export interface AutomationWorkerRun {
  id: UUID;
  workspaceId: UUID;
  workerType: "automation" | "webhook" | "email" | "import_conflict_resolution" | "import_review_export" | string;
  triggerType: "manual" | "scheduled" | string;
  status: "running" | "succeeded" | "failed" | string;
  dryRun: boolean;
  requestedLimit?: number;
  maxAttempts?: number;
  processedCount: number;
  successCount: number;
  failureCount: number;
  deadLetterCount: number;
  errorMessage?: string;
  metadata: unknown;
  startedAt: ISODateTime;
  finishedAt?: ISODateTime;
}

export interface AutomationWorkerRunRetention {
  workspaceId: UUID;
  workerType?: AutomationWorkerRun["workerType"];
  retentionEnabled: boolean;
  retentionDays?: number;
  exportBeforePrune: boolean;
  cutoff?: ISODateTime;
  runsEligible: number;
  runsIncluded: number;
  runsPruned: number;
  exportJobId?: UUID;
  fileAttachmentId?: UUID;
  runs: AutomationWorkerRun[];
}

export interface AutomationWorkerHealth {
  workspaceId: UUID;
  workerType: AutomationWorkerRun["workerType"];
  lastRunId?: UUID;
  lastStatus?: AutomationWorkerRun["status"];
  lastStartedAt?: ISODateTime;
  lastFinishedAt?: ISODateTime;
  consecutiveFailures: number;
  lastError?: string;
  updatedAt: ISODateTime;
}

export interface Webhook {
  id: UUID;
  workspaceId: UUID;
  name: string;
  url: string;
  secretConfigured: boolean;
  secretKeyId?: string;
  previousSecretKeyId?: string;
  secretRotatedAt?: ISODateTime;
  previousSecretExpiresAt?: ISODateTime;
  previousSecretOverlapSeconds: number;
  eventTypes: unknown;
  enabled: boolean;
}

export interface WebhookDelivery {
  id: UUID;
  webhookId: UUID;
  eventType: string;
  signatureKeyId?: string;
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
  status: "pending" | "imported" | "failed" | "skipped" | "conflict" | string;
  errorMessage?: string;
  rawPayload?: unknown;
  conflictStatus?: "open" | "resolved" | string;
  conflictReason?: string;
  conflictDetectedAt?: ISODateTime;
  conflictResolvedAt?: ISODateTime;
  conflictResolution?: "skip" | "update_existing" | "create_new" | string;
  conflictResolvedById?: UUID;
  conflictMaterializationRunId?: UUID;
}

export interface ImportJobRecordVersion {
  id: UUID;
  importJobRecordId: UUID;
  importJobId: UUID;
  version: number;
  changeType:
    | "created"
    | "updated"
    | "parsed"
    | "reparsed"
    | "materialized_created"
    | "materialized_updated"
    | "materialization_failed"
    | "conflict_opened"
    | "conflict_resolved"
    | string;
  changedById?: UUID;
  snapshot: unknown;
  createdAt: ISODateTime;
}

export interface ImportRecordListQuery {
  status?: "pending" | "imported" | "failed" | "skipped" | "conflict" | string;
  conflictStatus?: "open" | "resolved" | string;
  sourceType?: string;
}

export interface ImportJobRecordFieldDiff {
  path: string;
  changeType: "added" | "removed" | "changed";
  previousValue?: unknown;
  value?: unknown;
}

export interface ImportJobRecordVersionDiff {
  versionId: UUID;
  importJobRecordId: UUID;
  importJobId: UUID;
  version: number;
  comparedToVersion?: number;
  changeType: string;
  changedById?: UUID;
  createdAt: ISODateTime;
  fields: ImportJobRecordFieldDiff[];
}

export interface ImportJobRecordVersionDiffGroup {
  recordId: UUID;
  sourceType: string;
  sourceId: string;
  targetType?: string;
  targetId?: UUID;
  status: string;
  conflictStatus?: string;
  diffs: ImportJobRecordVersionDiff[];
}

export interface ImportJobVersionDiffResponse {
  importJobId: UUID;
  workspaceId: UUID;
  recordCount: number;
  versionCount: number;
  diffCount: number;
  records: ImportJobRecordVersionDiffGroup[];
}

export interface ImportJobVersionDiffExportResponse {
  generatedAt: ISODateTime;
  importJob: ImportJob;
  diffs: ImportJobVersionDiffResponse;
}

export interface ImportJobVersionDiffExportJobRequest {
  format?: "json" | "csv";
  filterColumn?: "all" | "source" | "statusLabel" | "versionLabel" | "changeType" | "path" | "previousValue" | "value" | string;
  filter?: string;
}

export interface ImportReviewCsvExportJobRequest {
  tableType: "conflict_resolution_jobs" | "export_jobs" | "project_completion" | "workspace_completion";
  importJobId?: UUID;
  projectId?: UUID;
  status?: string;
  exportType?: string;
  filterColumn?: string;
  filter?: string;
}

export interface ImportConflictBulkResolutionRequest {
  recordIds?: UUID[];
  resolution: "skip" | "update_existing" | "create_new";
  scope?: "selected" | "filtered";
  status?: string;
  conflictStatus?: "open";
  sourceType?: string;
  expectedCount?: number;
  confirmation?: "RESOLVE FILTERED CONFLICTS";
}

export interface ImportConflictBulkResolutionResponse {
  importJobId: UUID;
  resolution: ImportConflictBulkResolutionRequest["resolution"];
  scope: "selected" | "filtered";
  requested: number;
  resolved: number;
  records: ImportJobRecord[];
}

export interface ImportConflictBulkResolutionPreviewResponse {
  importJobId: UUID;
  resolution: ImportConflictBulkResolutionRequest["resolution"];
  scope: "selected" | "filtered";
  matched: number;
  returned: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
  maxResolutionBatchSize: number;
  records: ImportJobRecord[];
}

export interface ImportConflictResolutionJob {
  id: UUID;
  workspaceId: UUID;
  importJobId: UUID;
  requestedById?: UUID;
  resolution: ImportConflictBulkResolutionRequest["resolution"];
  scope: "filtered";
  status: "queued" | "running" | "completed" | "failed" | "cancelled" | string;
  statusFilter?: string;
  conflictStatusFilter?: "open";
  sourceTypeFilter?: string;
  expectedCount: number;
  matchedCount: number;
  resolvedCount: number;
  failedCount: number;
  errorMessage?: string;
  requestedAt: ISODateTime;
  startedAt?: ISODateTime;
  finishedAt?: ISODateTime;
}

export interface ImportConflictResolutionWorkerResponse {
  workspaceId?: UUID;
  processed: number;
  completed: number;
  failed: number;
  jobs: ImportConflictResolutionJob[];
}

export interface ImportJobCompleteRequest {
  acceptOpenConflicts?: boolean;
  openConflictConfirmation?: "COMPLETE WITH OPEN CONFLICTS";
  openConflictReason?: string;
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
  openConflictCompletionAccepted: boolean;
  openConflictCompletionCount: number;
  openConflictCompletedById?: UUID;
  openConflictCompletedAt?: ISODateTime;
  openConflictCompletionReason?: string;
  records: ImportJobRecord[];
}

export interface ImportSample {
  key: "csv" | "jira" | "rally" | string;
  label: string;
  provider: "csv" | "jira" | "rally" | string;
  sourceType: string;
  description: string;
}

export interface ImportWorkspaceSettings {
  workspaceId: UUID;
  sampleJobsEnabled: boolean;
  deploymentSampleJobsEnabled: boolean;
  sampleJobsAvailable: boolean;
  createdAt?: ISODateTime;
  updatedAt?: ISODateTime;
}

export interface ImportWorkspaceSettingsRequest {
  sampleJobsEnabled?: boolean;
}

export interface ImportSampleJobRequest {
  projectId?: UUID;
  createMappingTemplate?: boolean;
}

export interface ImportSampleJobResponse {
  sample: ImportSample;
  importJob: ImportJob;
  parse: unknown;
  transformPreset?: ImportTransformPreset;
  mappingTemplate?: ImportMappingTemplate;
}

export interface ImportTransformPreset {
  id: UUID;
  workspaceId: UUID;
  name: string;
  description?: string;
  transformationConfig: unknown;
  enabled: boolean;
  version: number;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface ImportTransformPresetVersion {
  id: UUID;
  presetId: UUID;
  workspaceId: UUID;
  version: number;
  name: string;
  description?: string;
  transformationConfig: unknown;
  enabled: boolean;
  changeType: "created" | "updated" | "disabled" | string;
  createdById?: UUID;
  createdAt: ISODateTime;
}

export interface ImportTransformPresetRetargetTemplate {
  id: UUID;
  name: string;
  provider: string;
  currentTransformPresetId?: UUID;
  newTransformPresetId?: UUID;
  willRetarget: boolean;
  reason?: string;
}

export interface ImportTransformPresetRetargetResponse {
  workspaceId: UUID;
  sourcePresetId: UUID;
  sourceVersionId: UUID;
  sourceVersion: number;
  clonedPreset?: ImportTransformPreset;
  cloneName: string;
  cloneDescription?: string;
  transformationConfig: unknown;
  enabled: boolean;
  templates: ImportTransformPresetRetargetTemplate[];
}

export interface ImportMaterializationRun {
  id: UUID;
  workspaceId: UUID;
  importJobId: UUID;
  mappingTemplateId?: UUID;
  transformPresetId?: UUID;
  transformPresetVersion?: number;
  projectId?: UUID;
  requestedById?: UUID;
  updateExisting: boolean;
  mappingTemplateSnapshot: unknown;
  mappingRulesSnapshot: unknown;
  transformPresetSnapshot?: unknown;
  transformationConfigSnapshot: unknown;
  status: "running" | "completed" | "completed_with_failures" | "failed" | string;
  recordsProcessed: number;
  recordsCreated: number;
  recordsUpdated: number;
  recordsFailed: number;
  recordsSkipped: number;
  recordsConflicted: number;
  createdAt: ISODateTime;
  finishedAt?: ISODateTime;
}

export interface ImportMappingTemplate {
  id: UUID;
  workspaceId: UUID;
  projectId?: UUID;
  transformPresetId?: UUID;
  name: string;
  provider: string;
  sourceType?: string;
  targetType: "work_item" | string;
  workItemTypeKey?: string;
  statusKey?: string;
  fieldMapping: unknown;
  defaults: unknown;
  transformationConfig: unknown;
  enabled: boolean;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
}

export interface AgentDispatchAttempt {
  id: UUID;
  workspaceId: UUID;
  agentTaskId: UUID;
  providerId: UUID;
  agentProfileId: UUID;
  workItemId: UUID;
  requestedById?: UUID;
  attemptType: "dispatch" | "retry" | "cancel";
  dispatchMode: string;
  providerType: string;
  transport?: string;
  status: "succeeded" | "failed";
  externalTaskId?: string;
  idempotencyKey?: string;
  externalDispatch: boolean;
  requestPayload: JsonObject;
  responsePayload: JsonObject;
  errorMessage?: string;
  startedAt: ISODateTime;
  finishedAt?: ISODateTime;
}

export interface AgentCliWorkerRun {
  agentTaskId: UUID;
  workspaceId: UUID;
  providerId: UUID;
  providerType?: string;
  agentProfileId: UUID;
  workItemId: UUID;
  status: string;
  createdAt: ISODateTime;
  updatedAt: ISODateTime;
  sizeBytes: number;
  fileCount: number;
  promptPresent: boolean;
  taskFilePresent: boolean;
  outputPresent: boolean;
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
  dispatchAttempts: AgentDispatchAttempt[];
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
6. Planning configuration: list/create boards with columns and saved-filter/query-backed swimlanes, read board work items with backend swimlane groupings, use board-scoped rank/transition/move commands for drag/drop, send `targetColumnId` and/or `targetStatusId` when deriving transitions from a target column/status, include card-level `previousWorkItemId`/`nextWorkItemId` from the target column when rank should update in the same board move, create releases and release scope, create project/workspace roadmaps, and add roadmap items linked to work items.
7. Automation configuration: create a notification preference, configure webhooks, create automation rules/actions, configure workspace Maildev/SMTP email provider settings, run `POST /api/v1/automation-rules/{ruleId}/execute`, then read automation jobs/logs, current-user notifications, webhook delivery rows, email delivery rows, worker runs, worker health, and scheduled worker settings. Webhook/email deliveries can be inspected, retried, cancelled, and processed manually; webhook secret rotation exposes previous-key overlap metadata, each webhook can set `previousSecretOverlapSeconds`, and each webhook delivery exposes the non-secret `signatureKeyId` selected when it was queued. Scheduled automation/webhook/email workers, scheduled import conflict-resolution pickup, scheduled import review CSV export pickup, worker run retention settings, and agent dispatch-attempt retention settings are controlled per workspace. Worker runs/health can be filtered with `workerType`; manual worker run retention export/prune also accepts `workerType`; automatic worker-run pruning can be toggled per workspace with interval/window/last-run settings. Import conflict-resolution pickup writes `AutomationWorkerRun`/`AutomationWorkerHealth` rows with `workerType=import_conflict_resolution`; import review export pickup writes rows with `workerType=import_review_export`.
8. Import review/materialization: create an import job, parse CSV/Jira/Rally content into records, or enable workspace sample jobs with `GET/PATCH /api/v1/workspaces/{workspaceId}/import-settings` and create a backend-generated demo job through `GET /api/v1/workspaces/{workspaceId}/import-samples` plus `POST /api/v1/workspaces/{workspaceId}/import-samples/{sampleKey}/jobs`. `GET /api/v1/import-jobs/{importJobId}/records` returns an empty list for jobs with no records; browser clients should still wait until the selected job has records or a reviewable lifecycle state before loading records, conflicts, version diffs, or materialization runs. Filter records by status/conflict/source type, edit source records with `PATCH /api/v1/import-job-records/{recordId}` when needed, show lifecycle versions from `GET /api/v1/import-job-records/{recordId}/versions` and backend-normalized field diff rows from `GET /api/v1/import-job-records/{recordId}/version-diffs`, show job-level field diff groups from `GET /api/v1/import-jobs/{importJobId}/version-diffs`, load audit-shaped exports from `GET /api/v1/import-jobs/{importJobId}/version-diffs/export`, create stored JSON or filtered CSV export-job artifacts with `POST /api/v1/import-jobs/{importJobId}/version-diffs/export-jobs`, create filtered CSV artifacts for conflict job/export job/project completion/workspace completion review tables with `POST /api/v1/workspaces/{workspaceId}/import-review/export-jobs`, list artifact history with `GET /api/v1/workspaces/{workspaceId}/export-jobs?exportType=...`, and download artifacts with `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}/download`. Create reusable versioned import transform presets, review preset version history with `GET /api/v1/import-transform-presets/{presetId}/versions`, clone historical preset versions into standalone presets, preview/apply clone-and-retarget of selected mapping templates, create an import mapping template that can reference a preset and optional local transformation overrides, add value lookups or type/status translations where needed, materialize records into work items, review/resolve single, selected bulk, or filtered bulk conflicts, rerun materialization snapshots with the source `updateExisting` flag or an explicit override, display materialization run snapshots including skipped/conflicted counters, and move the job through start/complete/fail/cancel states. Filtered bulk conflict resolution uses `POST /api/v1/import-jobs/{importJobId}/conflicts/resolve-preview` first; preview accepts `page` and `pageSize`, returns `matched`, `returned`, `hasMore`, and `maxResolutionBatchSize`, and synchronous apply succeeds only when `expectedCount` matches, `confirmation` is `RESOLVE FILTERED CONFLICTS`, and the matched set is within the server batch cap. Larger filtered sets can be persisted as queued jobs through `POST /api/v1/import-jobs/{importJobId}/conflicts/resolve-async`, listed through `GET /api/v1/import-jobs/{importJobId}/conflict-resolution-jobs` or `GET /api/v1/workspaces/{workspaceId}/import-conflict-resolution-jobs?status=...`, inspected through `GET /api/v1/import-conflict-resolution-jobs/{jobId}`, run individually through `POST /api/v1/import-conflict-resolution-jobs/{jobId}/run`, canceled/retried through `/cancel` and `/retry`, processed in bounded workspace batches through `POST /api/v1/workspaces/{workspaceId}/import-conflict-resolution-jobs/process?limit=...`, or picked up by workspace worker settings when `importConflictResolutionEnabled=true`. Completing with open conflicts requires `acceptOpenConflicts=true`, `openConflictConfirmation=COMPLETE WITH OPEN CONFLICTS`, and a non-empty `openConflictReason`; successful completion returns first-class `openConflictCompletion*` fields on the import job response and feeds project/workspace dashboard import-completion summaries plus `GET /api/v1/reports/projects/{projectId}/imports/completions` and `GET /api/v1/reports/workspaces/{workspaceId}/imports/completions`.
9. Dashboard builder: create a saved filter, optionally execute it with `GET /api/v1/saved-filters/{savedFilterId}/work-items`, create a governed report query catalog entry with optional `parametersSchema`, create a dashboard/widget, then render with `GET /api/v1/dashboards/{dashboardId}/render`. Import completion widgets use `widgetType=import_completion_summary` with `config.reportType=project_dashboard_summary` and `query.projectId`, or `widgetType=portfolio_import_completion_summary` with `config.reportType=workspace_dashboard_summary`.
10. Agent assignment: create provider/profile/repository connection, assign a work item with `POST /api/v1/work-items/{workItemId}/assign-agent`, then show task messages/artifacts/status and `dispatchAttempts` until review or completion. For Codex/Claude manual testing, the Agent page can create `cli_worker` providers from presets, save redacted provider credentials, rotate callback keys, preview runtime payloads, and rely on backend env-gated command profiles (`codex-local` and `claude-code-local`) when `TRASCK_AGENTS_CLI_WORKER_ENABLED=true` is set on a trusted host. The Agent page also owns CLI run artifact administration through `GET /api/v1/workspaces/{workspaceId}/agent-cli-runs`, ZIP download through `GET /api/v1/workspaces/{workspaceId}/agent-cli-runs/{agentTaskId}/download`, single-run delete through `DELETE /api/v1/workspaces/{workspaceId}/agent-cli-runs/{agentTaskId}`, and retention cleanup through `POST /api/v1/workspaces/{workspaceId}/agent-cli-runs/prune`.
11. Audit retention: update policy, export candidates to storage, then prune. Pruning writes a stored export before deleting eligible audit rows. Admin export history uses `GET /api/v1/workspaces/{workspaceId}/export-jobs`, metadata uses `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}`, and artifact download uses `GET /api/v1/workspaces/{workspaceId}/export-jobs/{exportJobId}/download`.
12. Reporting snapshots: run or backfill raw snapshots, optionally update `snapshot-retention-policy`, run/backfill rollups, then read `GET /api/v1/reports/projects/{projectId}/snapshots` for raw `series` plus additive `rollupSeries`.
13. Program reporting: create or update a workspace program with `GET/POST /api/v1/workspaces/{workspaceId}/programs` and `GET/PATCH/DELETE /api/v1/programs/{programId}`, assign projects with `PUT /api/v1/programs/{programId}/projects/{projectId}`, then read `GET /api/v1/reports/programs/{programId}/dashboard-summary`.
14. System and workspace/project security: active system admins can list/grant/revoke dedicated system-admin access through `GET/POST/DELETE /api/v1/system-admins`; production-like grant/revoke requires recent authentication; workspace admins can inspect and update effective attachment/import/export limits plus anonymous-read policy through `GET/PATCH /api/v1/workspaces/{workspaceId}/security-policy`; project admins can inspect inherited limits, update project visibility, and apply project overrides through `GET/PATCH /api/v1/projects/{projectId}/security-policy`; public project, work-item, comment, and attachment reads require both workspace anonymous-read and project `public` visibility. Anonymous work-item responses are intentionally narrower than authenticated work-item responses and omit user IDs such as reporter/assignee; public comment responses omit author/edit metadata; public attachment responses omit storage/uploader internals and include short-lived signed `downloadUrl` values that point at `GET /api/v1/public/projects/{projectId}/work-items/{workItemId}/attachments/{attachmentId}/download?token=...`. Workspace-admin member management can list safe invitation summaries with `GET /api/v1/workspaces/{workspaceId}/invitations?status=pending|accepted|revoked|all`, revoke pending invitations with `DELETE /api/v1/workspaces/{workspaceId}/invitations/{invitationId}`, list safe human workspace member summaries with `GET /api/v1/workspaces/{workspaceId}/users?status=active|removed|all`, create direct users with `POST /api/v1/workspaces/{workspaceId}/users`, and remove direct workspace users with `DELETE /api/v1/workspaces/{workspaceId}/users/{userId}`. Workspace/project role management can list permission catalogs, list/detail/create/update/archive roles, preview impacted members/invitations before permission edits, save permissions with a preview token, list role versions, and rollback through `GET/POST/PATCH/DELETE/PUT /api/v1/workspaces/{workspaceId}/roles...` and the equivalent `GET/POST/PATCH/DELETE/PUT /api/v1/projects/{projectId}/roles...` routes.

## Endpoint Coverage

- Setup: `POST /setup`
- Auth/security: login, current user, CSRF, personal tokens, workspace service tokens, invitations and invitation list/cancellation, human workspace user list/creation/removal, workspace/project role management, system-admin list/grant/revoke, workspace security-policy read/update including anonymous read, project security-policy read/update including visibility, and public project/work-item/comment/attachment reads with signed public attachment downloads.
- Work items: project list/create, typed single custom-field list filter, create/update keyed `customFields`, screen required-field enforcement on create/update, targeted required-field checks on assignee/team commands, detail/update/archive, assignment, rank, transition, team assignment, comments, links, watchers, work logs, workspace labels create/list/delete, work-item labels add/remove/list, attachments.
- Product configuration: custom field/context/value CRUD, field configuration CRUD with project/type overrides, screen/field/assignment CRUD.
- Teams/planning: team CRUD, memberships, project-team assignment, board/column/swimlane CRUD, saved-filter-ID and inline-query board swimlanes, board work item columns/swimlanes, board-scoped rank/transition/move commands with target column/status transition derivation and card-level relative insertion, iteration CRUD, scope, commit, close, carryover, release CRUD/scope, roadmap CRUD/items.
- Reporting/programs: program CRUD, program project assignment/removal, work item histories, work-log summary, project/workspace/program dashboard summaries, focused project/workspace import-completion metrics, snapshot run/backfill/reconcile, snapshot retention policy, rollup run/backfill, raw snapshots with `rollupSeries`, iteration reports.
- Dashboards/search: dashboard CRUD/render, widget CRUD, workspace/project/team dashboard lists, saved filter CRUD plus workspace/project/team lists and cursor-paged work item execution, saved view CRUD plus workspace/project/team lists, report query catalog CRUD plus workspace/project/team lists.
- Notifications/automation/import: current-user notifications, workspace-admin direct notification creation, notification read state, notification preferences, automation rule/condition/action CRUD, manual rule execution with job logs, worker settings including scheduled import conflict-resolution pickup, scheduled import review export pickup, and agent dispatch-attempt retention pruning, worker run/health/export/prune APIs with `import_conflict_resolution` and `import_review_export` worker rows and worker-type filters, automatic worker-run pruning interval/window settings, workspace email provider settings, webhook CRUD plus queued delivery records/retry/cancel/process APIs, Maildev/SMTP-backed email delivery records/retry/cancel/process APIs, import job lifecycle, workspace import sample settings, guarded backend sample catalog/job creation, parser, editable record APIs, filtered record lists, record version history and backend diff rows, job-level diff/export rows, stored JSON/CSV import diff export artifact history/download, stored CSV import review table exports for conflict jobs/export jobs/completion metrics, versioned transform preset plus immutable preset version history, clone-from-version, clone-retarget preview/apply, mapping-template, value lookup, transformation pipeline validation, type/status translation, materialization snapshot/skipped/conflicted counters, single/selected/filtered conflict review and resolution with paginated preview safeguards, queued filtered conflict-resolution jobs for larger sets, guarded completion with typed open-conflict confirmation and first-class audit/report fields, and exact/modified rerun APIs.
- Audit/admin: cursor-page audit log, audit retention policy/export/prune, cursor-page export jobs, export metadata/download, domain event replay.
- Agents: providers create/update/deactivate, credentials list/create/deactivate/re-encrypt, callback keys, Codex/Claude CLI runtime preview/readiness, profiles create/update/deactivate, repository connections create/list/deactivate, assignment, dispatch attempts, CLI run artifact list/download/delete/prune, worker dispatch, worker protocol, callbacks, task messages/artifacts/review actions.

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
