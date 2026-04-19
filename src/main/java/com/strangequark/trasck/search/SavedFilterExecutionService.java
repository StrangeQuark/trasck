package com.strangequark.trasck.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.api.CursorPageResponse;
import com.strangequark.trasck.api.PageCursorCodec;
import com.strangequark.trasck.customfield.CustomFieldSearchFilter;
import com.strangequark.trasck.customfield.CustomFieldService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workitem.WorkItemResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedFilterExecutionService {

    private static final List<String> BOOLEAN_GROUP_OPERATORS = List.of("and", "or");

    private final ObjectMapper objectMapper;
    private final SavedFilterService savedFilterService;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final WorkItemRepository workItemRepository;
    private final CustomFieldService customFieldService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final JdbcTemplate jdbcTemplate;

    public SavedFilterExecutionService(
            ObjectMapper objectMapper,
            SavedFilterService savedFilterService,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            WorkItemRepository workItemRepository,
            CustomFieldService customFieldService,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectMapper = objectMapper;
        this.savedFilterService = savedFilterService;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.workItemRepository = workItemRepository;
        this.customFieldService = customFieldService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<WorkItemResponse> executeWorkItems(UUID savedFilterId, Integer limit, String cursor) {
        UUID actorId = currentUserService.requireUserId();
        SavedFilter savedFilter = savedFilterService.requireReadableEntity(savedFilterId, actorId);
        JsonNode query = savedFilter.getQuery();
        validateEntityType(query);
        ExecutionScope scope = executionScope(savedFilter, query);
        requireWorkItemRead(actorId, savedFilter.getWorkspaceId(), scope);
        SortSpec sort = sortSpec(query, scope);
        SqlPredicate predicate = filterPredicate(query, savedFilter.getWorkspaceId());

        int pageLimit = normalizePageLimit(limit);
        List<WorkItem> page = executeQuery(savedFilter.getWorkspaceId(), scope, predicate, sort, cursor, pageLimit + 1);
        boolean hasMore = page.size() > pageLimit;
        List<WorkItem> items = hasMore ? page.subList(0, pageLimit) : page;
        String nextCursor = hasMore ? encodeCursor(sort, items.get(items.size() - 1)) : null;
        return new CursorPageResponse<>(
                items.stream().map(WorkItemResponse::from).toList(),
                nextCursor,
                hasMore,
                pageLimit
        );
    }

    private List<WorkItem> executeQuery(
            UUID workspaceId,
            ExecutionScope scope,
            SqlPredicate predicate,
            SortSpec sort,
            String cursor,
            int limit
    ) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(workspaceId);
        StringBuilder where = new StringBuilder("""
                wi.workspace_id = ?
                  and wi.deleted_at is null
                  and p.deleted_at is null
                  and p.status = 'active'
                """);
        if (!scope.projectIds().isEmpty()) {
            where.append(" and wi.project_id in (").append(placeholders(scope.projectIds().size())).append(")");
            parameters.addAll(scope.projectIds());
        }
        if (scope.teamId() != null) {
            where.append(" and wi.team_id = ?");
            parameters.add(scope.teamId());
        }
        if (!"1 = 1".equals(predicate.sql())) {
            where.append(" and (").append(predicate.sql()).append(")");
            parameters.addAll(predicate.parameters());
        }
        if (hasText(cursor)) {
            CursorPredicate cursorPredicate = cursorPredicate(sort, cursor);
            where.append(" and ").append(cursorPredicate.sql());
            parameters.addAll(cursorPredicate.parameters());
        }
        parameters.add(limit);

        String sql = """
                select wi.id
                from work_items wi
                join projects p on p.id = wi.project_id
                join work_item_types wit on wit.id = wi.type_id
                join workflow_statuses ws on ws.id = wi.status_id
                left join priorities pr on pr.id = wi.priority_id
                where %s
                order by %s %s, wi.id::text asc
                limit ?
                """.formatted(where, sort.expression(), sort.direction());
        List<UUID> ids = jdbcTemplate.query(
                sql,
                ps -> {
                    for (int i = 0; i < parameters.size(); i++) {
                        ps.setObject(i + 1, parameters.get(i));
                    }
                },
                (rs, rowNum) -> (UUID) rs.getObject("id")
        );
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, WorkItem> byId = new LinkedHashMap<>();
        workItemRepository.findAllById(ids).forEach(item -> byId.put(item.getId(), item));
        return ids.stream()
                .map(byId::get)
                .filter(item -> item != null)
                .toList();
    }

    private ExecutionScope executionScope(SavedFilter savedFilter, JsonNode query) {
        Set<UUID> projectIds = new LinkedHashSet<>();
        addProjectScope(projectIds, savedFilter.getWorkspaceId(), query.path("projectId"));
        JsonNode projectIdsNode = query.path("projectIds");
        if (!projectIdsNode.isMissingNode() && !projectIdsNode.isNull()) {
            if (!projectIdsNode.isArray()) {
                throw badRequest("query.projectIds must be an array");
            }
            projectIdsNode.forEach(projectId -> addProjectScope(projectIds, savedFilter.getWorkspaceId(), projectId));
        }

        if (savedFilter.getProjectId() != null) {
            if (!projectIds.isEmpty() && !projectIds.equals(Set.of(savedFilter.getProjectId()))) {
                throw badRequest("query project scope must stay within the saved filter project");
            }
            projectIds.clear();
            projectIds.add(activeProject(savedFilter.getProjectId(), savedFilter.getWorkspaceId()).getId());
        }

        UUID queryTeamId = parseOptionalUuid(query.path("teamId"), "query.teamId must be a UUID");
        UUID teamId = savedFilter.getTeamId() == null ? queryTeamId : savedFilter.getTeamId();
        if (savedFilter.getTeamId() != null && queryTeamId != null && !savedFilter.getTeamId().equals(queryTeamId)) {
            throw badRequest("query team scope must stay within the saved filter team");
        }
        if (teamId != null) {
            activeTeam(teamId, savedFilter.getWorkspaceId());
        }
        return new ExecutionScope(List.copyOf(projectIds), teamId);
    }

    private void addProjectScope(Set<UUID> projectIds, UUID workspaceId, JsonNode value) {
        UUID projectId = parseOptionalUuid(value, "query project scope must be a UUID");
        if (projectId != null) {
            projectIds.add(activeProject(projectId, workspaceId).getId());
        }
    }

    private Project activeProject(UUID projectId, UUID workspaceId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> badRequest("query project scope is not in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("query project scope is not in this workspace");
        }
        return project;
    }

    private Team activeTeam(UUID teamId, UUID workspaceId) {
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, workspaceId)
                .orElseThrow(() -> badRequest("query team scope is not in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw badRequest("query team scope is not active");
        }
        return team;
    }

    private void requireWorkItemRead(UUID actorId, UUID workspaceId, ExecutionScope scope) {
        if (scope.projectIds().isEmpty()) {
            permissionService.requireWorkspacePermission(actorId, workspaceId, "work_item.read");
            return;
        }
        scope.projectIds().forEach(projectId -> permissionService.requireProjectPermission(actorId, projectId, "work_item.read"));
    }

    private SqlPredicate filterPredicate(JsonNode query, UUID workspaceId) {
        JsonNode where = query.path("where");
        if (!where.isMissingNode() && !where.isNull()) {
            return predicate(where, workspaceId);
        }
        JsonNode filters = query.path("filters");
        if (!filters.isMissingNode() && !filters.isNull()) {
            return groupPredicate("and", filters, workspaceId);
        }
        return new SqlPredicate("1 = 1", List.of());
    }

    private SqlPredicate predicate(JsonNode node, UUID workspaceId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new SqlPredicate("1 = 1", List.of());
        }
        if (node.isArray()) {
            return groupPredicate("and", node, workspaceId);
        }
        if (!node.isObject()) {
            throw badRequest("query predicate must be an object");
        }
        JsonNode conditions = firstPresent(node.path("conditions"), node.path("predicates"), node.path("filters"));
        if (!conditions.isMissingNode()) {
            String op = normalizeGroupOperator(text(node.path("op"), "and"));
            return groupPredicate(op, conditions, workspaceId);
        }
        if (isCustomFieldPredicate(node)) {
            return customFieldPredicate(node, workspaceId);
        }
        return systemFieldPredicate(node);
    }

    private SqlPredicate groupPredicate(String op, JsonNode conditions, UUID workspaceId) {
        if (!conditions.isArray()) {
            throw badRequest("query predicate conditions must be an array");
        }
        List<SqlPredicate> children = new ArrayList<>();
        conditions.forEach(child -> {
            SqlPredicate predicate = predicate(child, workspaceId);
            if (!"1 = 1".equals(predicate.sql())) {
                children.add(predicate);
            }
        });
        if (children.isEmpty()) {
            return new SqlPredicate("1 = 1", List.of());
        }
        List<Object> parameters = new ArrayList<>();
        List<String> sql = new ArrayList<>();
        children.forEach(child -> {
            sql.add("(" + child.sql() + ")");
            parameters.addAll(child.parameters());
        });
        return new SqlPredicate(String.join(" " + op + " ", sql), parameters);
    }

    private SqlPredicate systemFieldPredicate(JsonNode node) {
        FieldDefinition field = fieldDefinition(requiredText(text(node.path("field"), null), "predicate.field"));
        String operator = normalizeOperator(text(node.path("operator"), "eq"));
        if ("is_null".equals(operator)) {
            return new SqlPredicate(field.expression() + " is null", List.of());
        }
        if ("is_not_null".equals(operator)) {
            return new SqlPredicate(field.expression() + " is not null", List.of());
        }
        List<String> values = predicateValues(node, operator);
        return switch (field.type()) {
            case TEXT -> textPredicate(field.expression(), operator, values);
            case UUID -> uuidPredicate(field.expression(), operator, values);
            case LONG -> longPredicate(field.expression(), operator, values);
            case INTEGER -> integerPredicate(field.expression(), operator, values);
            case DECIMAL -> decimalPredicate(field.expression(), operator, values);
            case DATE -> datePredicate(field.expression(), operator, values);
            case TIMESTAMP -> timestampPredicate(field.expression(), operator, values);
        };
    }

    private SqlPredicate customFieldPredicate(JsonNode node, UUID workspaceId) {
        String operator = normalizeOperator(text(node.path("operator"), "eq"));
        String customFieldReference = customFieldReference(node);
        if ("is_null".equals(operator) || "is_not_null".equals(operator)) {
            CustomFieldSearchFilter filter = customFieldService.resolveSearchableFieldForWorkspace(workspaceId, customFieldReference);
            if ("is_not_null".equals(operator)) {
                return new SqlPredicate("""
                        exists (
                            select 1
                            from custom_field_values cfv
                            where cfv.work_item_id = wi.id
                              and cfv.custom_field_id = ?
                              and cfv.value is not null
                              and cfv.value <> 'null'::jsonb
                        )
                        """, List.of(filter.customFieldId()));
            }
            return new SqlPredicate("""
                    not exists (
                        select 1
                        from custom_field_values cfv
                        where cfv.work_item_id = wi.id
                          and cfv.custom_field_id = ?
                          and cfv.value is not null
                          and cfv.value <> 'null'::jsonb
                    )
                    """, List.of(filter.customFieldId()));
        }
        List<String> values = predicateValues(node, operator);
        CustomFieldSearchFilter filter = customFieldService.resolveSearchableFieldForWorkspace(
                workspaceId,
                customFieldReference,
                operator,
                values
        );
        CustomFieldSqlPredicate customPredicate = customFieldSqlPredicate(filter);
        List<Object> parameters = new ArrayList<>();
        parameters.add(filter.customFieldId());
        parameters.addAll(customPredicate.parameters());
        return new SqlPredicate("""
                exists (
                    select 1
                    from custom_field_values cfv
                    where cfv.work_item_id = wi.id
                      and cfv.custom_field_id = ?
                      and %s
                )
                """.formatted(customPredicate.sql()), parameters);
    }

    private boolean isCustomFieldPredicate(JsonNode node) {
        if (hasText(text(node.path("customFieldKey"), null)) || hasText(text(node.path("customFieldId"), null))) {
            return true;
        }
        String field = text(node.path("field"), null);
        return hasText(field) && (field.startsWith("custom.") || field.startsWith("customField."));
    }

    private String customFieldReference(JsonNode node) {
        String customFieldId = text(node.path("customFieldId"), null);
        if (hasText(customFieldId)) {
            return customFieldId;
        }
        String customFieldKey = text(node.path("customFieldKey"), null);
        if (hasText(customFieldKey)) {
            return customFieldKey;
        }
        String field = requiredText(text(node.path("field"), null), "predicate.field");
        if (field.startsWith("custom.")) {
            return requiredText(field.substring("custom.".length()), "customFieldKey");
        }
        if (field.startsWith("customField.")) {
            return requiredText(field.substring("customField.".length()), "customFieldKey");
        }
        throw badRequest("customFieldKey or customFieldId is required");
    }

    private FieldDefinition fieldDefinition(String field) {
        return switch (normalizeField(field)) {
            case "id" -> new FieldDefinition("wi.id", FieldType.UUID);
            case "projectid" -> new FieldDefinition("wi.project_id", FieldType.UUID);
            case "typeid" -> new FieldDefinition("wi.type_id", FieldType.UUID);
            case "typekey" -> new FieldDefinition("wit.key", FieldType.TEXT);
            case "statusid" -> new FieldDefinition("wi.status_id", FieldType.UUID);
            case "statuskey" -> new FieldDefinition("ws.key", FieldType.TEXT);
            case "statuscategory" -> new FieldDefinition("ws.category", FieldType.TEXT);
            case "priorityid" -> new FieldDefinition("wi.priority_id", FieldType.UUID);
            case "prioritykey" -> new FieldDefinition("pr.key", FieldType.TEXT);
            case "resolutionid" -> new FieldDefinition("wi.resolution_id", FieldType.UUID);
            case "teamid" -> new FieldDefinition("wi.team_id", FieldType.UUID);
            case "assigneeid" -> new FieldDefinition("wi.assignee_id", FieldType.UUID);
            case "reporterid" -> new FieldDefinition("wi.reporter_id", FieldType.UUID);
            case "parentid" -> new FieldDefinition("wi.parent_id", FieldType.UUID);
            case "createdbyid" -> new FieldDefinition("wi.created_by_id", FieldType.UUID);
            case "updatedbyid" -> new FieldDefinition("wi.updated_by_id", FieldType.UUID);
            case "key" -> new FieldDefinition("wi.key", FieldType.TEXT);
            case "title" -> new FieldDefinition("wi.title", FieldType.TEXT);
            case "visibility" -> new FieldDefinition("wi.visibility", FieldType.TEXT);
            case "rank" -> new FieldDefinition("wi.rank", FieldType.TEXT);
            case "sequencenumber" -> new FieldDefinition("wi.sequence_number", FieldType.LONG);
            case "workspacesequencenumber" -> new FieldDefinition("wi.workspace_sequence_number", FieldType.LONG);
            case "estimatepoints" -> new FieldDefinition("wi.estimate_points", FieldType.DECIMAL);
            case "estimateminutes" -> new FieldDefinition("wi.estimate_minutes", FieldType.INTEGER);
            case "remainingminutes" -> new FieldDefinition("wi.remaining_minutes", FieldType.INTEGER);
            case "startdate" -> new FieldDefinition("wi.start_date", FieldType.DATE);
            case "duedate" -> new FieldDefinition("wi.due_date", FieldType.DATE);
            case "resolvedat" -> new FieldDefinition("wi.resolved_at", FieldType.TIMESTAMP);
            case "createdat" -> new FieldDefinition("wi.created_at", FieldType.TIMESTAMP);
            case "updatedat" -> new FieldDefinition("wi.updated_at", FieldType.TIMESTAMP);
            default -> throw badRequest("Unsupported work item predicate field: " + field);
        };
    }

    private SqlPredicate textPredicate(String expression, String operator, List<String> values) {
        return switch (operator) {
            case "eq" -> new SqlPredicate(expression + " = ?", List.of(values.get(0)));
            case "ne" -> new SqlPredicate("coalesce(" + expression + ", '') <> ?", List.of(values.get(0)));
            case "contains" -> new SqlPredicate("position(lower(?) in lower(coalesce(" + expression + ", ''))) > 0", List.of(values.get(0)));
            case "not_contains" -> new SqlPredicate("position(lower(?) in lower(coalesce(" + expression + ", ''))) = 0", List.of(values.get(0)));
            case "in" -> new SqlPredicate(expression + " in (" + placeholders(values.size()) + ")", List.copyOf(values));
            default -> throw badRequest("Unsupported operator for text field");
        };
    }

    private SqlPredicate uuidPredicate(String expression, String operator, List<String> values) {
        values.forEach(value -> parseUuid(value, "predicate value must be a UUID"));
        return switch (operator) {
            case "eq" -> new SqlPredicate(expression + " = cast(? as uuid)", List.of(values.get(0)));
            case "ne" -> new SqlPredicate(expression + " <> cast(? as uuid)", List.of(values.get(0)));
            case "in" -> new SqlPredicate(expression + " in (" + castPlaceholders(values.size(), "uuid") + ")", List.copyOf(values));
            default -> throw badRequest("Unsupported operator for UUID field");
        };
    }

    private SqlPredicate longPredicate(String expression, String operator, List<String> values) {
        List<Long> parsed = values.stream().map(value -> parseLong(value, "predicate value must be a whole number")).toList();
        return comparablePredicate(expression, operator, parsed);
    }

    private SqlPredicate integerPredicate(String expression, String operator, List<String> values) {
        List<Integer> parsed = values.stream().map(value -> parseInteger(value, "predicate value must be an integer")).toList();
        return comparablePredicate(expression, operator, parsed);
    }

    private SqlPredicate decimalPredicate(String expression, String operator, List<String> values) {
        List<BigDecimal> parsed = values.stream().map(value -> parseDecimal(value, "predicate value must be a number")).toList();
        return comparablePredicate(expression, operator, parsed);
    }

    private SqlPredicate datePredicate(String expression, String operator, List<String> values) {
        List<LocalDate> parsed = values.stream().map(value -> parseDate(value, "predicate value must be an ISO-8601 date")).toList();
        return comparablePredicate(expression, operator, parsed);
    }

    private SqlPredicate timestampPredicate(String expression, String operator, List<String> values) {
        List<OffsetDateTime> parsed = values.stream().map(value -> parseDateTime(value, "predicate value must be an ISO-8601 datetime with offset")).toList();
        return comparablePredicate(expression, operator, parsed);
    }

    private SqlPredicate comparablePredicate(String expression, String operator, List<?> values) {
        return switch (operator) {
            case "eq" -> new SqlPredicate(expression + " = ?", List.of(values.get(0)));
            case "ne" -> new SqlPredicate(expression + " <> ?", List.of(values.get(0)));
            case "gt" -> new SqlPredicate(expression + " > ?", List.of(values.get(0)));
            case "gte" -> new SqlPredicate(expression + " >= ?", List.of(values.get(0)));
            case "lt" -> new SqlPredicate(expression + " < ?", List.of(values.get(0)));
            case "lte" -> new SqlPredicate(expression + " <= ?", List.of(values.get(0)));
            case "between" -> new SqlPredicate(expression + " between ? and ?", List.of(values.get(0), values.get(1)));
            case "in" -> new SqlPredicate(expression + " in (" + placeholders(values.size()) + ")", List.copyOf(values));
            default -> throw badRequest("Unsupported operator for comparable field");
        };
    }

    private CustomFieldSqlPredicate customFieldSqlPredicate(CustomFieldSearchFilter filter) {
        String expression = "cfv.value #>> '{}'";
        return switch (filter.fieldType()) {
            case "text", "textarea", "single_select", "user", "url" -> customScalarTextPredicate(expression, filter.operator(), filter.values());
            case "number", "integer" -> customComparablePredicate("cast(" + expression + " as numeric)", "numeric", filter.operator(), filter.values());
            case "date" -> customComparablePredicate("cast(" + expression + " as date)", "date", filter.operator(), filter.values());
            case "datetime" -> customComparablePredicate("cast(" + expression + " as timestamptz)", "timestamptz", filter.operator(), filter.values());
            case "boolean" -> customBooleanPredicate(expression, filter.operator(), filter.values());
            case "multi_select" -> customMultiSelectPredicate(filter.operator(), filter.values());
            case "json" -> customJsonPredicate(filter.operator(), filter.values());
            default -> throw badRequest("Unsupported custom field type");
        };
    }

    private CustomFieldSqlPredicate customScalarTextPredicate(String expression, String operator, List<String> values) {
        return switch (operator) {
            case "eq" -> new CustomFieldSqlPredicate(expression + " = ?", List.of(values.get(0)));
            case "ne" -> new CustomFieldSqlPredicate("coalesce(" + expression + ", '') <> ?", List.of(values.get(0)));
            case "contains" -> new CustomFieldSqlPredicate("position(lower(?) in lower(coalesce(" + expression + ", ''))) > 0", List.of(values.get(0)));
            case "not_contains" -> new CustomFieldSqlPredicate("position(lower(?) in lower(coalesce(" + expression + ", ''))) = 0", List.of(values.get(0)));
            case "in" -> new CustomFieldSqlPredicate(expression + " in (" + placeholders(values.size()) + ")", List.copyOf(values));
            default -> throw badRequest("Unsupported custom field operator");
        };
    }

    private CustomFieldSqlPredicate customComparablePredicate(String expression, String castType, String operator, List<String> values) {
        return switch (operator) {
            case "eq" -> new CustomFieldSqlPredicate(expression + " = cast(? as " + castType + ")", List.of(values.get(0)));
            case "ne" -> new CustomFieldSqlPredicate(expression + " <> cast(? as " + castType + ")", List.of(values.get(0)));
            case "gt" -> new CustomFieldSqlPredicate(expression + " > cast(? as " + castType + ")", List.of(values.get(0)));
            case "gte" -> new CustomFieldSqlPredicate(expression + " >= cast(? as " + castType + ")", List.of(values.get(0)));
            case "lt" -> new CustomFieldSqlPredicate(expression + " < cast(? as " + castType + ")", List.of(values.get(0)));
            case "lte" -> new CustomFieldSqlPredicate(expression + " <= cast(? as " + castType + ")", List.of(values.get(0)));
            case "between" -> new CustomFieldSqlPredicate(
                    expression + " between cast(? as " + castType + ") and cast(? as " + castType + ")",
                    List.of(values.get(0), values.get(1))
            );
            case "in" -> new CustomFieldSqlPredicate(expression + " in (" + castPlaceholders(values.size(), castType) + ")", List.copyOf(values));
            default -> throw badRequest("Unsupported custom field operator");
        };
    }

    private CustomFieldSqlPredicate customBooleanPredicate(String expression, String operator, List<String> values) {
        return switch (operator) {
            case "eq" -> new CustomFieldSqlPredicate("cast(" + expression + " as boolean) = cast(? as boolean)", List.of(values.get(0)));
            case "ne" -> new CustomFieldSqlPredicate("cast(" + expression + " as boolean) <> cast(? as boolean)", List.of(values.get(0)));
            default -> throw badRequest("Unsupported custom field operator");
        };
    }

    private CustomFieldSqlPredicate customMultiSelectPredicate(String operator, List<String> values) {
        return switch (operator) {
            case "contains" -> new CustomFieldSqlPredicate("jsonb_exists(cfv.value, ?)", List.of(values.get(0)));
            case "not_contains" -> new CustomFieldSqlPredicate("not jsonb_exists(cfv.value, ?)", List.of(values.get(0)));
            case "in" -> new CustomFieldSqlPredicate("jsonb_exists_any(cfv.value, array[" + placeholders(values.size()) + "]::text[])", List.copyOf(values));
            default -> throw badRequest("Unsupported custom field operator");
        };
    }

    private CustomFieldSqlPredicate customJsonPredicate(String operator, List<String> values) {
        return switch (operator) {
            case "eq" -> new CustomFieldSqlPredicate("cfv.value = cast(? as jsonb)", List.of(values.get(0)));
            case "ne" -> new CustomFieldSqlPredicate("cfv.value <> cast(? as jsonb)", List.of(values.get(0)));
            default -> throw badRequest("Unsupported custom field operator");
        };
    }

    private SortSpec sortSpec(JsonNode query, ExecutionScope scope) {
        JsonNode sortNode = query.path("sort");
        JsonNode sort = sortNode.isArray() && !sortNode.isEmpty() ? sortNode.get(0) : sortNode;
        String field = sort.isObject() ? text(sort.path("field"), "workspaceSequenceNumber") : "workspaceSequenceNumber";
        String direction = sort.isObject() ? text(sort.path("direction"), "asc").toLowerCase() : "asc";
        if (!List.of("asc", "desc").contains(direction)) {
            throw badRequest("sort direction must be asc or desc");
        }
        return switch (normalizeField(field)) {
            case "rank" -> {
                if (scope.projectIds().size() != 1) {
                    throw badRequest("rank sort requires a single project scope");
                }
                yield new SortSpec("rank", "wi.rank", direction, CursorKind.RANK);
            }
            case "workspacesequencenumber" -> new SortSpec("workspaceSequenceNumber", "wi.workspace_sequence_number", direction, CursorKind.LONG);
            default -> throw badRequest("Unsupported saved filter sort field: " + field);
        };
    }

    private CursorPredicate cursorPredicate(SortSpec sort, String cursor) {
        String operator = "asc".equals(sort.direction()) ? ">" : "<";
        if (sort.kind() == CursorKind.RANK) {
            PageCursorCodec.RankCursor decoded = PageCursorCodec.decodeRank(cursor);
            return new CursorPredicate(
                    "(" + sort.expression() + " " + operator + " ? or (" + sort.expression() + " = ? and wi.id::text > ?))",
                    List.of(decoded.rank(), decoded.rank(), decoded.id())
            );
        }
        PageCursorCodec.LongCursor decoded = PageCursorCodec.decodeLong(cursor);
        return new CursorPredicate(
                "(" + sort.expression() + " " + operator + " ? or (" + sort.expression() + " = ? and wi.id::text > ?))",
                List.of(decoded.value(), decoded.value(), decoded.id())
        );
    }

    private String encodeCursor(SortSpec sort, WorkItem item) {
        if (sort.kind() == CursorKind.RANK) {
            return PageCursorCodec.encodeRank(item.getRank(), item.getId().toString());
        }
        return PageCursorCodec.encodeLong(item.getWorkspaceSequenceNumber(), item.getId().toString());
    }

    private List<String> predicateValues(JsonNode node, String operator) {
        if ("between".equals(operator)) {
            JsonNode values = node.path("values");
            if (values.isArray()) {
                List<String> entries = valuesFromArray(values);
                if (entries.size() != 2) {
                    throw badRequest("predicate.values must contain exactly two values for between");
                }
                return entries;
            }
            return List.of(
                    scalarValue(requiredNode(node.path("value"), "predicate.value is required")),
                    scalarValue(requiredNode(node.path("valueTo"), "predicate.valueTo is required"))
            );
        }
        JsonNode values = node.path("values");
        if ("in".equals(operator) && values.isArray()) {
            List<String> entries = valuesFromArray(values);
            if (entries.isEmpty()) {
                throw badRequest("predicate.values must contain at least one value");
            }
            return entries;
        }
        JsonNode value = requiredNode(node.path("value"), "predicate.value is required");
        if ("in".equals(operator) && value.isTextual() && value.asText().contains(",")) {
            List<String> entries = List.of(value.asText().split(",")).stream()
                    .map(String::trim)
                    .filter(this::hasText)
                    .toList();
            if (entries.isEmpty()) {
                throw badRequest("predicate.value must contain at least one value");
            }
            return entries;
        }
        return List.of(scalarValue(value));
    }

    private List<String> valuesFromArray(JsonNode values) {
        List<String> entries = new ArrayList<>();
        values.forEach(value -> entries.add(scalarValue(value)));
        return entries;
    }

    private JsonNode requiredNode(JsonNode value, String message) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw badRequest(message);
        }
        return value;
    }

    private String scalarValue(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw badRequest("predicate value must be valid JSON");
        }
    }

    private void validateEntityType(JsonNode query) {
        String entityType = text(query.path("entityType"), null);
        if (!hasText(entityType)) {
            return;
        }
        String normalized = entityType.trim().toLowerCase();
        if (!List.of("work_item", "work-items", "workitem", "workitems").contains(normalized)) {
            throw badRequest("saved filter execution only supports work item queries");
        }
    }

    private String normalizeGroupOperator(String operator) {
        String normalized = hasText(operator) ? operator.trim().toLowerCase() : "and";
        if (!BOOLEAN_GROUP_OPERATORS.contains(normalized)) {
            throw badRequest("query group op must be and or or");
        }
        return normalized;
    }

    private String normalizeOperator(String operator) {
        String normalized = hasText(operator) ? operator.trim().toLowerCase() : "eq";
        if ("neq".equals(normalized)) {
            normalized = "ne";
        }
        if (!List.of("eq", "ne", "contains", "not_contains", "in", "gt", "gte", "lt", "lte", "between", "is_null", "is_not_null").contains(normalized)) {
            throw badRequest("Unsupported saved filter predicate operator: " + operator);
        }
        return normalized;
    }

    private JsonNode firstPresent(JsonNode first, JsonNode second, JsonNode third) {
        if (!first.isMissingNode()) {
            return first;
        }
        if (!second.isMissingNode()) {
            return second;
        }
        return third;
    }

    private UUID parseOptionalUuid(JsonNode value, String message) {
        if (value.isMissingNode() || value.isNull() || !hasText(value.asText(null))) {
            return null;
        }
        return parseUuid(value.asText(), message);
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw badRequest(message);
        }
    }

    private Long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw badRequest(message);
        }
    }

    private Integer parseInteger(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw badRequest(message);
        }
    }

    private BigDecimal parseDecimal(String value, String message) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw badRequest(message);
        }
    }

    private LocalDate parseDate(String value, String message) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw badRequest(message);
        }
    }

    private OffsetDateTime parseDateTime(String value, String message) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw badRequest(message);
        }
    }

    private String normalizeField(String field) {
        return field.trim().replace("_", "").replace("-", "").toLowerCase();
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String castPlaceholders(int count, String castType) {
        return String.join(", ", java.util.Collections.nCopies(count, "cast(? as " + castType + ")"));
    }

    private int normalizePageLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.max(1, Math.min(limit, 100));
    }

    private String text(JsonNode node, String defaultValue) {
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asText(defaultValue);
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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private enum FieldType {
        TEXT,
        UUID,
        LONG,
        INTEGER,
        DECIMAL,
        DATE,
        TIMESTAMP
    }

    private enum CursorKind {
        RANK,
        LONG
    }

    private record ExecutionScope(List<UUID> projectIds, UUID teamId) {
    }

    private record FieldDefinition(String expression, FieldType type) {
    }

    private record SortSpec(String field, String expression, String direction, CursorKind kind) {
    }

    private record SqlPredicate(String sql, List<?> parameters) {
    }

    private record CursorPredicate(String sql, List<?> parameters) {
    }

    private record CustomFieldSqlPredicate(String sql, List<?> parameters) {
    }
}
