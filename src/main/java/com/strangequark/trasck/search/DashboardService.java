package com.strangequark.trasck.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.reporting.PortfolioReportSummaryResponse;
import com.strangequark.trasck.reporting.ProjectReportSummaryResponse;
import com.strangequark.trasck.reporting.ReportingService;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamMembershipRepository;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {

    private final ObjectMapper objectMapper;
    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final ReportingService reportingService;
    private final ReportQueryCatalogService reportQueryCatalogService;
    private final DomainEventService domainEventService;

    public DashboardService(
            ObjectMapper objectMapper,
            DashboardRepository dashboardRepository,
            DashboardWidgetRepository dashboardWidgetRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMembershipRepository teamMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            ReportingService reportingService,
            ReportQueryCatalogService reportQueryCatalogService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.dashboardRepository = dashboardRepository;
        this.dashboardWidgetRepository = dashboardWidgetRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.reportingService = reportingService;
        this.reportQueryCatalogService = reportQueryCatalogService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<DashboardResponse> list(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.read");
        boolean canManage = canManageReports(actorId, workspaceId);
        return dashboardRepository.findVisibleCandidates(workspaceId, actorId).stream()
                .filter(dashboard -> canReadDashboard(actorId, dashboard, canManage))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardResponse> listByProject(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "report.read");
        boolean canManage = canManageReports(actorId, project.getWorkspaceId());
        return dashboardRepository.findByWorkspaceIdAndVisibilityAndProjectIdOrderByNameAsc(project.getWorkspaceId(), "project", project.getId()).stream()
                .filter(dashboard -> canReadDashboard(actorId, dashboard, canManage))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DashboardResponse> listByTeam(UUID teamId) {
        UUID actorId = currentUserService.requireUserId();
        Team team = activeTeam(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "report.read");
        boolean canManage = canManageReports(actorId, team.getWorkspaceId());
        return dashboardRepository.findByWorkspaceIdAndVisibilityAndTeamIdOrderByNameAsc(team.getWorkspaceId(), "team", team.getId()).stream()
                .filter(dashboard -> canReadDashboard(actorId, dashboard, canManage))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(UUID dashboardId) {
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireReadable(actorId, dashboard);
        return response(dashboard);
    }

    @Transactional
    public DashboardResponse create(UUID workspaceId, DashboardRequest request) {
        DashboardRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        String visibility = normalizeVisibility(createRequest.visibility());
        UUID projectId = normalizeProjectId(workspaceId, visibility, createRequest.projectId());
        UUID teamId = normalizeTeamId(workspaceId, visibility, createRequest.teamId());
        requireSharedPermission(actorId, workspaceId, visibility, projectId);

        Dashboard dashboard = new Dashboard();
        dashboard.setWorkspaceId(workspaceId);
        dashboard.setOwnerId(actorId);
        dashboard.setProjectId(projectId);
        dashboard.setTeamId(teamId);
        dashboard.setName(requiredText(createRequest.name(), "name"));
        dashboard.setVisibility(visibility);
        dashboard.setLayout(toJson(createRequest.layout()));
        Dashboard saved = dashboardRepository.save(dashboard);
        recordDashboardEvent(saved, "dashboard.created", actorId);
        return response(saved);
    }

    @Transactional
    public DashboardResponse update(UUID dashboardId, DashboardRequest request) {
        DashboardRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireWritable(actorId, dashboard);
        String targetVisibility = hasText(updateRequest.visibility())
                ? normalizeVisibility(updateRequest.visibility())
                : dashboard.getVisibility();
        UUID requestedProjectId = updateRequest.projectId();
        if (requestedProjectId == null && "project".equals(targetVisibility) && "project".equals(dashboard.getVisibility())) {
            requestedProjectId = dashboard.getProjectId();
        }
        UUID requestedTeamId = updateRequest.teamId();
        if (requestedTeamId == null && "team".equals(targetVisibility) && "team".equals(dashboard.getVisibility())) {
            requestedTeamId = dashboard.getTeamId();
        }
        UUID targetProjectId = hasText(updateRequest.visibility()) || updateRequest.projectId() != null
                ? normalizeProjectId(dashboard.getWorkspaceId(), targetVisibility, requestedProjectId)
                : dashboard.getProjectId();
        UUID targetTeamId = hasText(updateRequest.visibility()) || updateRequest.teamId() != null
                ? normalizeTeamId(dashboard.getWorkspaceId(), targetVisibility, requestedTeamId)
                : dashboard.getTeamId();
        requireSharedPermission(actorId, dashboard.getWorkspaceId(), targetVisibility, targetProjectId);

        if (hasText(updateRequest.name())) {
            dashboard.setName(updateRequest.name().trim());
        }
        dashboard.setVisibility(targetVisibility);
        dashboard.setProjectId(targetProjectId);
        dashboard.setTeamId(targetTeamId);
        if (updateRequest.layout() != null) {
            dashboard.setLayout(toJson(updateRequest.layout()));
        }
        Dashboard saved = dashboardRepository.save(dashboard);
        recordDashboardEvent(saved, "dashboard.updated", actorId);
        return response(saved);
    }

    @Transactional
    public void delete(UUID dashboardId) {
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireWritable(actorId, dashboard);
        dashboardRepository.delete(dashboard);
        recordDashboardEvent(dashboard, "dashboard.deleted", actorId);
    }

    @Transactional
    public DashboardWidgetResponse createWidget(UUID dashboardId, DashboardWidgetRequest request) {
        DashboardWidgetRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireWritable(actorId, dashboard);
        DashboardWidget widget = new DashboardWidget();
        widget.setDashboardId(dashboard.getId());
        applyWidgetRequest(widget, createRequest, true);
        DashboardWidget saved = dashboardWidgetRepository.save(widget);
        recordDashboardEvent(dashboard, "dashboard.widget_created", actorId);
        return DashboardWidgetResponse.from(saved);
    }

    @Transactional
    public DashboardWidgetResponse updateWidget(UUID dashboardId, UUID widgetId, DashboardWidgetRequest request) {
        DashboardWidgetRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireWritable(actorId, dashboard);
        DashboardWidget widget = dashboardWidgetRepository.findById(widgetId)
                .filter(existing -> dashboard.getId().equals(existing.getDashboardId()))
                .orElseThrow(() -> notFound("Dashboard widget not found"));
        applyWidgetRequest(widget, updateRequest, false);
        DashboardWidget saved = dashboardWidgetRepository.save(widget);
        recordDashboardEvent(dashboard, "dashboard.widget_updated", actorId);
        return DashboardWidgetResponse.from(saved);
    }

    @Transactional
    public void deleteWidget(UUID dashboardId, UUID widgetId) {
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireWritable(actorId, dashboard);
        DashboardWidget widget = dashboardWidgetRepository.findById(widgetId)
                .filter(existing -> dashboard.getId().equals(existing.getDashboardId()))
                .orElseThrow(() -> notFound("Dashboard widget not found"));
        dashboardWidgetRepository.delete(widget);
        recordDashboardEvent(dashboard, "dashboard.widget_deleted", actorId);
    }

    @Transactional(readOnly = true)
    public DashboardRenderResponse render(UUID dashboardId, String from, String to) {
        UUID actorId = currentUserService.requireUserId();
        Dashboard dashboard = dashboard(dashboardId);
        activeWorkspace(dashboard.getWorkspaceId());
        requireReadable(actorId, dashboard);
        List<DashboardWidget> widgets = widgets(dashboard.getId());
        List<DashboardWidgetRenderResponse> rendered = new ArrayList<>();
        OffsetDateTime renderedFrom = null;
        OffsetDateTime renderedTo = null;
        for (DashboardWidget widget : widgets) {
            RenderedWidgetData data = renderWidgetData(dashboard, widget, from, to);
            if (renderedFrom == null) {
                renderedFrom = data.from();
                renderedTo = data.to();
            }
            rendered.add(new DashboardWidgetRenderResponse(widget.getId(), widget.getWidgetType(), widget.getTitle(), data.data()));
        }
        return new DashboardRenderResponse(DashboardResponse.from(dashboard, widgets), renderedFrom, renderedTo, rendered);
    }

    private RenderedWidgetData renderWidgetData(Dashboard dashboard, DashboardWidget widget, String from, String to) {
        JsonNode rawConfig = widget.getConfig() == null || widget.getConfig().isNull() ? objectMapper.createObjectNode() : widget.getConfig();
        ObjectNode config = reportQueryCatalogService.resolveWidgetConfig(dashboard.getWorkspaceId(), rawConfig);
        JsonNode query = config.path("query").isObject() ? config.path("query") : config;
        String reportType = normalizeKey(firstText(text(config, "reportType"), widget.getWidgetType()));
        String effectiveFrom = firstText(from, text(query, "from"));
        String effectiveTo = firstText(to, text(query, "to"));
        return switch (reportType) {
            case "project_dashboard_summary" -> {
                UUID projectId = requiredUuid(query, "projectId");
                ProjectReportSummaryResponse summary = reportingService.projectDashboardSummary(
                        projectId,
                        effectiveFrom,
                        effectiveTo,
                        optionalUuid(query, "teamId"),
                        optionalUuid(query, "iterationId")
                );
                yield new RenderedWidgetData(summary.from(), summary.to(), selectProjectWidgetData(widget.getWidgetType(), summary));
            }
            case "workspace_dashboard_summary", "portfolio_dashboard_summary" -> {
                PortfolioReportSummaryResponse summary = reportingService.workspaceDashboardSummary(
                        dashboard.getWorkspaceId(),
                        effectiveFrom,
                        effectiveTo,
                        uuidList(query.path("projectIds"))
                );
                yield new RenderedWidgetData(summary.from(), summary.to(), selectPortfolioWidgetData(widget.getWidgetType(), summary));
            }
            case "program_dashboard_summary" -> {
                UUID programId = requiredUuid(query, "programId");
                PortfolioReportSummaryResponse summary = reportingService.programDashboardSummary(programId, effectiveFrom, effectiveTo);
                yield new RenderedWidgetData(summary.from(), summary.to(), selectPortfolioWidgetData(widget.getWidgetType(), summary));
            }
            default -> new RenderedWidgetData(null, null, Map.of(
                    "status", "unrendered",
                    "reason", "No reporting renderer is registered for this widget reportType.",
                    "reportType", reportType
            ));
        };
    }

    private Object selectProjectWidgetData(String widgetType, ProjectReportSummaryResponse summary) {
        return switch (normalizeKey(widgetType)) {
            case "work_item_summary" -> summary.workItems();
            case "throughput" -> summary.throughput();
            case "estimate_time_summary" -> summary.estimateAndTime();
            case "aging_wip" -> summary.aging();
            case "cycle_time_inputs" -> summary.cycleTime();
            case "import_completion_summary" -> summary.importCompletions();
            case "work_by_status" -> summary.byStatus();
            case "work_by_type" -> summary.byType();
            case "work_by_priority" -> summary.byPriority();
            default -> summary;
        };
    }

    private Object selectPortfolioWidgetData(String widgetType, PortfolioReportSummaryResponse summary) {
        return switch (normalizeKey(widgetType)) {
            case "work_item_summary", "portfolio_work_item_summary" -> summary.workItems();
            case "throughput", "portfolio_throughput" -> summary.throughput();
            case "estimate_time_summary", "portfolio_estimate_time_summary" -> summary.estimateAndTime();
            case "cycle_time_inputs", "portfolio_cycle_time_inputs" -> summary.cycleTime();
            case "import_completion_summary", "portfolio_import_completion_summary" -> summary.importCompletions();
            case "work_by_project" -> summary.byProject();
            case "work_by_team" -> summary.byTeam();
            case "work_by_type" -> summary.byType();
            default -> summary;
        };
    }

    private void applyWidgetRequest(DashboardWidget widget, DashboardWidgetRequest request, boolean creating) {
        if (creating || hasText(request.widgetType())) {
            widget.setWidgetType(normalizeKey(requiredText(request.widgetType(), "widgetType")));
        }
        if (request.title() != null) {
            widget.setTitle(hasText(request.title()) ? request.title().trim() : null);
        }
        if (creating || request.config() != null) {
            widget.setConfig(validatedWidgetConfig(request.config()));
        }
        if (creating || request.positionX() != null) {
            widget.setPositionX(nonNegative(firstNonNull(request.positionX(), 0), "positionX"));
        }
        if (creating || request.positionY() != null) {
            widget.setPositionY(nonNegative(firstNonNull(request.positionY(), 0), "positionY"));
        }
        if (creating || request.width() != null) {
            widget.setWidth(positive(firstNonNull(request.width(), 1), "width"));
        }
        if (creating || request.height() != null) {
            widget.setHeight(positive(firstNonNull(request.height(), 1), "height"));
        }
    }

    private JsonNode validatedWidgetConfig(Object config) {
        JsonNode json = toJson(config);
        if (!json.isObject()) {
            throw badRequest("widget config must be a JSON object");
        }
        if (containsRawSqlKey(json)) {
            throw badRequest("dashboard widgets must use reporting API query config, not raw SQL");
        }
        JsonNode query = json.path("query");
        if (!query.isMissingNode() && !query.isObject()) {
            throw badRequest("widget config.query must be a JSON object");
        }
        return json;
    }

    private boolean containsRawSqlKey(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("sql".equalsIgnoreCase(field.getKey()) || "rawSql".equalsIgnoreCase(field.getKey())) {
                    return true;
                }
                if (containsRawSqlKey(field.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsRawSqlKey(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private DashboardResponse response(Dashboard dashboard) {
        return DashboardResponse.from(dashboard, widgets(dashboard.getId()));
    }

    private List<DashboardWidget> widgets(UUID dashboardId) {
        return dashboardWidgetRepository.findByDashboardIdOrderByPositionYAscPositionXAsc(dashboardId);
    }

    private Dashboard dashboard(UUID dashboardId) {
        return dashboardRepository.findById(dashboardId).orElseThrow(() -> notFound("Dashboard not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> notFound("Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        activeWorkspace(project.getWorkspaceId());
        return project;
    }

    private Team activeTeam(UUID teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> notFound("Team not found"));
        if (!"active".equals(team.getStatus())) {
            throw notFound("Team not found");
        }
        activeWorkspace(team.getWorkspaceId());
        return team;
    }

    private UUID normalizeTeamId(UUID workspaceId, String visibility, UUID teamId) {
        if (!"team".equals(visibility)) {
            if (teamId != null) {
                throw badRequest("teamId is only valid for team dashboards");
            }
            return null;
        }
        if (teamId == null) {
            throw badRequest("teamId is required for team dashboards");
        }
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, workspaceId)
                .orElseThrow(() -> badRequest("Team not found in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw badRequest("Team is not active");
        }
        return team.getId();
    }

    private UUID normalizeProjectId(UUID workspaceId, String visibility, UUID projectId) {
        if (!"project".equals(visibility)) {
            if (projectId != null) {
                throw badRequest("projectId is only valid for project dashboards");
            }
            return null;
        }
        if (projectId == null) {
            throw badRequest("projectId is required for project dashboards");
        }
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> badRequest("Project not found in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("Project not found in this workspace");
        }
        return project.getId();
    }

    private void requireReadable(UUID actorId, Dashboard dashboard) {
        if (!canReadDashboard(actorId, dashboard, canManageReports(actorId, dashboard.getWorkspaceId()))) {
            throw notFound("Dashboard not found");
        }
    }

    private boolean canReadDashboard(UUID actorId, Dashboard dashboard, boolean canManage) {
        if (canManage) {
            return true;
        }
        if (actorId.equals(dashboard.getOwnerId())) {
            if ("project".equals(dashboard.getVisibility()) && dashboard.getProjectId() != null) {
                return canReadProjectReports(actorId, dashboard.getProjectId())
                        || canUseWorkspace(actorId, dashboard.getWorkspaceId(), "report.read");
            }
            return canUseWorkspace(actorId, dashboard.getWorkspaceId(), "report.read");
        }
        if ("workspace".equals(dashboard.getVisibility()) || "public".equals(dashboard.getVisibility())) {
            return canUseWorkspace(actorId, dashboard.getWorkspaceId(), "report.read");
        }
        if ("project".equals(dashboard.getVisibility()) && dashboard.getProjectId() != null) {
            return canReadProjectReports(actorId, dashboard.getProjectId());
        }
        return "team".equals(dashboard.getVisibility())
                && dashboard.getTeamId() != null
                && canUseWorkspace(actorId, dashboard.getWorkspaceId(), "report.read")
                && teamMembershipRepository.existsByTeamIdAndUserIdAndLeftAtIsNull(dashboard.getTeamId(), actorId);
    }

    private void requireWritable(UUID actorId, Dashboard dashboard) {
        if ("private".equals(dashboard.getVisibility())
                && actorId.equals(dashboard.getOwnerId())
                && canUseWorkspace(actorId, dashboard.getWorkspaceId(), "report.read")) {
            return;
        }
        if ("project".equals(dashboard.getVisibility())
                && dashboard.getProjectId() != null
                && canManageProjectReports(actorId, dashboard.getProjectId())) {
            return;
        }
        if (canManageReports(actorId, dashboard.getWorkspaceId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to manage this dashboard");
    }

    private void requireSharedPermission(UUID actorId, UUID workspaceId, String visibility, UUID projectId) {
        if ("private".equals(visibility)) {
            permissionService.requireWorkspacePermission(actorId, workspaceId, "report.read");
            return;
        }
        if ("project".equals(visibility)) {
            permissionService.requireProjectPermission(actorId, required(projectId, "projectId"), "report.manage");
            return;
        }
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.manage");
    }

    private boolean canManageReports(UUID actorId, UUID workspaceId) {
        return permissionService.canUseWorkspace(actorId, workspaceId, "report.manage");
    }

    private boolean canUseWorkspace(UUID actorId, UUID workspaceId, String permissionKey) {
        return permissionService.canUseWorkspace(actorId, workspaceId, permissionKey);
    }

    private boolean canManageProjectReports(UUID actorId, UUID projectId) {
        return permissionService.canUseProject(actorId, projectId, "report.manage");
    }

    private boolean canReadProjectReports(UUID actorId, UUID projectId) {
        return permissionService.canUseProject(actorId, projectId, "report.read");
    }

    private void recordDashboardEvent(Dashboard dashboard, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("dashboardId", dashboard.getId().toString())
                .put("dashboardName", dashboard.getName())
                .put("visibility", dashboard.getVisibility())
                .put("actorUserId", actorId.toString());
        if (dashboard.getProjectId() != null) {
            payload.put("projectId", dashboard.getProjectId().toString());
        }
        if (dashboard.getTeamId() != null) {
            payload.put("teamId", dashboard.getTeamId().toString());
        }
        domainEventService.record(dashboard.getWorkspaceId(), "dashboard", dashboard.getId(), eventType, payload);
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private UUID requiredUuid(JsonNode node, String fieldName) {
        UUID id = optionalUuid(node, fieldName);
        if (id == null) {
            throw badRequest("widget query." + fieldName + " is required");
        }
        return id;
    }

    private UUID optionalUuid(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !hasText(value.asText(null))) {
            return null;
        }
        return parseUuid(value.asText(), "widget query." + fieldName + " must be a UUID");
    }

    private List<UUID> uuidList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw badRequest("widget query.projectIds must be an array");
        }
        List<UUID> ids = new ArrayList<>();
        node.forEach(value -> ids.add(parseUuid(value.asText(), "widget query.projectIds must contain only UUID values")));
        return ids;
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw badRequest(message);
        }
    }

    private String normalizeVisibility(String visibility) {
        String normalized = hasText(visibility) ? visibility.trim().toLowerCase() : "private";
        if (!List.of("private", "team", "project", "workspace", "public").contains(normalized)) {
            throw badRequest("visibility must be private, team, project, workspace, or public");
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        return requiredText(value, "key").toLowerCase().replace('-', '_').replace(' ', '_');
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private int nonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw badRequest(fieldName + " must be zero or greater");
        }
        return value;
    }

    private int positive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw badRequest(fieldName + " must be greater than zero");
        }
        return value;
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw badRequest(fieldName + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record RenderedWidgetData(OffsetDateTime from, OffsetDateTime to, Object data) {
    }
}
