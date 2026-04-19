package com.strangequark.trasck.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamMembershipRepository;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportQueryCatalogService {

    private final ObjectMapper objectMapper;
    private final ReportQueryCatalogRepository reportQueryCatalogRepository;
    private final SavedFilterRepository savedFilterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public ReportQueryCatalogService(
            ObjectMapper objectMapper,
            ReportQueryCatalogRepository reportQueryCatalogRepository,
            SavedFilterRepository savedFilterRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMembershipRepository teamMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.reportQueryCatalogRepository = reportQueryCatalogRepository;
        this.savedFilterRepository = savedFilterRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<ReportQueryCatalogResponse> list(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.read");
        boolean canManageWorkspaceReports = canUseWorkspace(actorId, workspaceId, "report.manage");
        return reportQueryCatalogRepository.findVisibleCandidates(workspaceId, actorId).stream()
                .filter(entry -> canRead(actorId, entry, canManageWorkspaceReports))
                .map(ReportQueryCatalogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportQueryCatalogResponse get(UUID queryId) {
        UUID actorId = currentUserService.requireUserId();
        ReportQueryCatalogEntry entry = entry(queryId);
        activeWorkspace(entry.getWorkspaceId());
        requireReadable(actorId, entry);
        return ReportQueryCatalogResponse.from(entry);
    }

    @Transactional
    public ReportQueryCatalogResponse create(UUID workspaceId, ReportQueryCatalogRequest request) {
        ReportQueryCatalogRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        String visibility = normalizeVisibility(createRequest.visibility());
        UUID projectId = normalizeProjectId(workspaceId, visibility, createRequest.projectId());
        UUID teamId = normalizeTeamId(workspaceId, visibility, createRequest.teamId());
        requireManage(actorId, workspaceId, visibility, projectId);
        String queryKey = normalizeKey(requiredText(createRequest.queryKey(), "queryKey"));
        if (reportQueryCatalogRepository.existsByWorkspaceIdAndQueryKeyIgnoreCase(workspaceId, queryKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report query key already exists");
        }

        ReportQueryCatalogEntry entry = new ReportQueryCatalogEntry();
        entry.setWorkspaceId(workspaceId);
        entry.setOwnerId(actorId);
        entry.setProjectId(projectId);
        entry.setTeamId(teamId);
        entry.setQueryKey(queryKey);
        entry.setName(requiredText(createRequest.name(), "name"));
        entry.setDescription(trimToNull(createRequest.description()));
        entry.setQueryType(normalizeQueryType(createRequest.queryType()));
        entry.setQueryConfig(validatedConfig(createRequest.queryConfig(), "queryConfig"));
        entry.setParametersSchema(validatedParametersSchema(createRequest.parametersSchema()));
        entry.setVisibility(visibility);
        entry.setEnabled(createRequest.enabled() == null || Boolean.TRUE.equals(createRequest.enabled()));
        ReportQueryCatalogEntry saved = reportQueryCatalogRepository.save(entry);
        recordEvent(saved, "report_query.created", actorId);
        return ReportQueryCatalogResponse.from(saved);
    }

    @Transactional
    public ReportQueryCatalogResponse update(UUID queryId, ReportQueryCatalogRequest request) {
        ReportQueryCatalogRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        ReportQueryCatalogEntry entry = entry(queryId);
        requireWritable(actorId, entry);
        String targetVisibility = hasText(updateRequest.visibility())
                ? normalizeVisibility(updateRequest.visibility())
                : entry.getVisibility();
        UUID requestedProjectId = updateRequest.projectId();
        if (requestedProjectId == null && "project".equals(targetVisibility) && "project".equals(entry.getVisibility())) {
            requestedProjectId = entry.getProjectId();
        }
        UUID requestedTeamId = updateRequest.teamId();
        if (requestedTeamId == null && "team".equals(targetVisibility) && "team".equals(entry.getVisibility())) {
            requestedTeamId = entry.getTeamId();
        }
        UUID targetProjectId = hasText(updateRequest.visibility()) || updateRequest.projectId() != null
                ? normalizeProjectId(entry.getWorkspaceId(), targetVisibility, requestedProjectId)
                : entry.getProjectId();
        UUID targetTeamId = hasText(updateRequest.visibility()) || updateRequest.teamId() != null
                ? normalizeTeamId(entry.getWorkspaceId(), targetVisibility, requestedTeamId)
                : entry.getTeamId();
        requireManage(actorId, entry.getWorkspaceId(), targetVisibility, targetProjectId);

        if (hasText(updateRequest.queryKey())) {
            String targetKey = normalizeKey(updateRequest.queryKey());
            if (!entry.getQueryKey().equalsIgnoreCase(targetKey)
                    && reportQueryCatalogRepository.existsByWorkspaceIdAndQueryKeyIgnoreCase(entry.getWorkspaceId(), targetKey)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Report query key already exists");
            }
            entry.setQueryKey(targetKey);
        }
        if (hasText(updateRequest.name())) {
            entry.setName(updateRequest.name().trim());
        }
        if (updateRequest.description() != null) {
            entry.setDescription(trimToNull(updateRequest.description()));
        }
        if (hasText(updateRequest.queryType())) {
            entry.setQueryType(normalizeQueryType(updateRequest.queryType()));
        }
        if (updateRequest.queryConfig() != null) {
            entry.setQueryConfig(validatedConfig(updateRequest.queryConfig(), "queryConfig"));
        }
        if (updateRequest.parametersSchema() != null) {
            entry.setParametersSchema(validatedParametersSchema(updateRequest.parametersSchema()));
        }
        if (updateRequest.enabled() != null) {
            entry.setEnabled(updateRequest.enabled());
        }
        entry.setVisibility(targetVisibility);
        entry.setProjectId(targetProjectId);
        entry.setTeamId(targetTeamId);
        ReportQueryCatalogEntry saved = reportQueryCatalogRepository.save(entry);
        recordEvent(saved, "report_query.updated", actorId);
        return ReportQueryCatalogResponse.from(saved);
    }

    @Transactional
    public void delete(UUID queryId) {
        UUID actorId = currentUserService.requireUserId();
        ReportQueryCatalogEntry entry = entry(queryId);
        requireWritable(actorId, entry);
        reportQueryCatalogRepository.delete(entry);
        recordEvent(entry, "report_query.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public ObjectNode resolveWidgetConfig(UUID workspaceId, JsonNode widgetConfig) {
        UUID actorId = currentUserService.requireUserId();
        ObjectNode config = toObject(widgetConfig, "widget config");
        UUID reportQueryId = firstUuid(config, config.path("query"), "reportQueryId");
        ReportQueryCatalogEntry reportQueryEntry = null;
        ObjectNode resolved = config.deepCopy();
        if (reportQueryId != null) {
            ReportQueryCatalogEntry entry = entry(reportQueryId);
            if (!workspaceId.equals(entry.getWorkspaceId()) || !Boolean.TRUE.equals(entry.getEnabled())) {
                throw notFound("Report query not found");
            }
            requireReadable(actorId, entry);
            reportQueryEntry = entry;
            resolved = objectMapper.createObjectNode();
            resolved.put("reportType", entry.getQueryType());
            resolved.set("query", toObject(entry.getQueryConfig(), "report query config"));
            mergeObject((ObjectNode) resolved.path("query"), config, "query");
        }

        resolveSavedFilter(actorId, workspaceId, resolved);
        if (reportQueryEntry != null) {
            validateRuntimeParameters(reportQueryEntry, resolved.path("query").isObject() ? resolved.path("query") : resolved);
        }
        return resolved;
    }

    private void resolveSavedFilter(UUID actorId, UUID workspaceId, ObjectNode config) {
        ObjectNode query = config.path("query").isObject()
                ? (ObjectNode) config.path("query")
                : config;
        UUID savedFilterId = firstUuid(config, query, "savedFilterId");
        if (savedFilterId == null) {
            return;
        }
        SavedFilter savedFilter = savedFilterRepository.findById(savedFilterId)
                .orElseThrow(() -> notFound("Saved filter not found"));
        if (!workspaceId.equals(savedFilter.getWorkspaceId())) {
            throw notFound("Saved filter not found");
        }
        requireSavedFilterReadable(actorId, savedFilter);
        ObjectNode mergedQuery = toObject(savedFilter.getQuery(), "saved filter query");
        mergeObject(mergedQuery, query, null);
        if (mergedQuery.has("reportType") && !config.has("reportType")) {
            config.put("reportType", mergedQuery.path("reportType").asText());
            mergedQuery.remove("reportType");
        }
        config.set("query", mergedQuery);
    }

    private JsonNode validatedConfig(Object config, String fieldName) {
        ObjectNode json = toObject(toJson(config), fieldName);
        if (containsRawSqlKey(json)) {
            throw badRequest(fieldName + " must not contain raw SQL");
        }
        return json;
    }

    private JsonNode validatedParametersSchema(Object config) {
        ObjectNode schema = toObject(toJson(config), "parametersSchema");
        if (containsRawSqlKey(schema)) {
            throw badRequest("parametersSchema must not contain raw SQL");
        }
        if (schema.isEmpty()) {
            return schema;
        }
        JsonNode rootType = schema.get("type");
        if (rootType != null && !rootType.isNull() && (!rootType.isTextual() || !"object".equals(rootType.asText()))) {
            throw badRequest("parametersSchema.type must be object");
        }
        if (schema.has("required") && !schema.path("required").isArray()) {
            throw badRequest("parametersSchema.required must be an array");
        }
        if (schema.has("additionalProperties") && !schema.path("additionalProperties").isBoolean()) {
            throw badRequest("parametersSchema.additionalProperties must be a boolean");
        }
        ObjectNode definitions = parameterDefinitions(schema);
        for (String requiredParameter : requiredParameters(schema, definitions)) {
            if (!definitions.has(requiredParameter)) {
                throw badRequest("parametersSchema.required references unknown parameter " + requiredParameter);
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = definitions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isObject()) {
                throw badRequest("parametersSchema." + field.getKey() + " must be an object");
            }
            validateParameterDefinition(field.getKey(), (ObjectNode) field.getValue());
        }
        return schema;
    }

    private void validateParameterDefinition(String parameterName, ObjectNode definition) {
        String type = parameterType(parameterName, definition);
        if (!List.of("string", "uuid", "integer", "number", "boolean", "date", "datetime", "array", "object").contains(type)) {
            throw badRequest("parametersSchema." + parameterName + ".type is not supported");
        }
        if (definition.has("required") && !definition.path("required").isBoolean()) {
            throw badRequest("parametersSchema." + parameterName + ".required must be a boolean");
        }
        if (definition.has("enum") && !definition.path("enum").isArray()) {
            throw badRequest("parametersSchema." + parameterName + ".enum must be an array");
        }
        if ("array".equals(type) && definition.has("items")) {
            JsonNode items = definition.path("items");
            if (!items.isObject()) {
                throw badRequest("parametersSchema." + parameterName + ".items must be an object");
            }
            validateParameterDefinition(parameterName + ".items", (ObjectNode) items);
        }
    }

    private void validateRuntimeParameters(ReportQueryCatalogEntry entry, JsonNode query) {
        JsonNode schema = entry.getParametersSchema();
        if (schema == null || !schema.isObject() || schema.isEmpty()) {
            return;
        }
        ObjectNode parameters = toObject(query, "widget query");
        ObjectNode definitions = parameterDefinitions(schema);
        Set<String> required = requiredParameters(schema, definitions);
        for (String parameterName : required) {
            JsonNode value = parameters.get(parameterName);
            if (value == null || value.isNull() || (value.isTextual() && !hasText(value.asText()))) {
                throw badRequest("widget query." + parameterName + " is required by report query " + entry.getQueryKey());
            }
        }

        Iterator<Map.Entry<String, JsonNode>> fields = parameters.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (isControlParameter(field.getKey())) {
                continue;
            }
            JsonNode definition = definitions.get(field.getKey());
            if (definition == null || definition.isMissingNode()) {
                if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean()) {
                    throw badRequest("widget query." + field.getKey() + " is not allowed by report query " + entry.getQueryKey());
                }
                continue;
            }
            validateRuntimeParameter(field.getKey(), field.getValue(), (ObjectNode) definition);
        }
    }

    private ObjectNode parameterDefinitions(JsonNode schema) {
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            return (ObjectNode) properties;
        }
        JsonNode parameters = schema.path("parameters");
        if (parameters.isObject()) {
            return (ObjectNode) parameters;
        }
        ObjectNode direct = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = schema.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!List.of("type", "required", "additionalProperties").contains(field.getKey())) {
                direct.set(field.getKey(), field.getValue());
            }
        }
        return direct;
    }

    private Set<String> requiredParameters(JsonNode schema, ObjectNode definitions) {
        Set<String> required = new HashSet<>();
        JsonNode rootRequired = schema.path("required");
        if (rootRequired.isArray()) {
            for (JsonNode value : rootRequired) {
                if (!value.isTextual() || !hasText(value.asText())) {
                    throw badRequest("parametersSchema.required must contain parameter names");
                }
                required.add(value.asText());
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = definitions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().path("required").asBoolean(false)) {
                required.add(field.getKey());
            }
        }
        return required;
    }

    private void validateRuntimeParameter(String parameterName, JsonNode value, ObjectNode definition) {
        if (value == null || value.isNull()) {
            return;
        }
        String type = parameterType(parameterName, definition);
        switch (type) {
            case "string" -> requireTextValue(parameterName, value);
            case "uuid" -> parseUuid(requireTextValue(parameterName, value), "widget query." + parameterName + " must be a UUID");
            case "integer" -> requireIntegerValue(parameterName, value);
            case "number" -> requireNumberValue(parameterName, value);
            case "boolean" -> requireBooleanValue(parameterName, value);
            case "date" -> requireDateValue(parameterName, value);
            case "datetime" -> requireDateTimeValue(parameterName, value);
            case "array" -> requireArrayValue(parameterName, value, definition.path("items"));
            case "object" -> {
                if (!value.isObject()) {
                    throw badRequest("widget query." + parameterName + " must be an object");
                }
            }
            default -> throw badRequest("parametersSchema." + parameterName + ".type is not supported");
        }
        validateEnumValue(parameterName, value, definition.path("enum"));
    }

    private String parameterType(String parameterName, JsonNode definition) {
        JsonNode type = definition.path("type");
        if (!type.isTextual() || !hasText(type.asText())) {
            throw badRequest("parametersSchema." + parameterName + ".type is required");
        }
        return normalizeKey(type.asText());
    }

    private String requireTextValue(String parameterName, JsonNode value) {
        if (!value.isTextual() || !hasText(value.asText())) {
            throw badRequest("widget query." + parameterName + " must be a string");
        }
        return value.asText();
    }

    private void requireIntegerValue(String parameterName, JsonNode value) {
        if (value.isIntegralNumber()) {
            return;
        }
        if (value.isTextual()) {
            try {
                Integer.parseInt(value.asText());
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        throw badRequest("widget query." + parameterName + " must be an integer");
    }

    private void requireNumberValue(String parameterName, JsonNode value) {
        if (value.isNumber()) {
            return;
        }
        if (value.isTextual()) {
            try {
                Double.parseDouble(value.asText());
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        throw badRequest("widget query." + parameterName + " must be a number");
    }

    private void requireBooleanValue(String parameterName, JsonNode value) {
        if (value.isBoolean()) {
            return;
        }
        if (value.isTextual() && List.of("true", "false").contains(value.asText().toLowerCase())) {
            return;
        }
        throw badRequest("widget query." + parameterName + " must be a boolean");
    }

    private void requireDateValue(String parameterName, JsonNode value) {
        try {
            LocalDate.parse(requireTextValue(parameterName, value));
        } catch (Exception ex) {
            throw badRequest("widget query." + parameterName + " must be an ISO-8601 date");
        }
    }

    private void requireDateTimeValue(String parameterName, JsonNode value) {
        try {
            OffsetDateTime.parse(requireTextValue(parameterName, value));
        } catch (Exception ex) {
            throw badRequest("widget query." + parameterName + " must be an ISO-8601 timestamp");
        }
    }

    private void requireArrayValue(String parameterName, JsonNode value, JsonNode itemDefinition) {
        if (!value.isArray()) {
            throw badRequest("widget query." + parameterName + " must be an array");
        }
        if (itemDefinition == null || !itemDefinition.isObject() || itemDefinition.isEmpty()) {
            return;
        }
        for (JsonNode item : value) {
            validateRuntimeParameter(parameterName + "[]", item, (ObjectNode) itemDefinition);
        }
    }

    private void validateEnumValue(String parameterName, JsonNode value, JsonNode allowedValues) {
        if (allowedValues == null || !allowedValues.isArray() || allowedValues.isEmpty()) {
            return;
        }
        for (JsonNode allowedValue : allowedValues) {
            if (allowedValue.asText().equals(value.asText())) {
                return;
            }
        }
        throw badRequest("widget query." + parameterName + " is not an allowed value");
    }

    private boolean isControlParameter(String parameterName) {
        return "reportQueryId".equals(parameterName) || "savedFilterId".equals(parameterName);
    }

    private ObjectNode toObject(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!node.isObject()) {
            throw badRequest(fieldName + " must be a JSON object");
        }
        return (ObjectNode) node;
    }

    private ObjectNode toObject(Object value, String fieldName) {
        return toObject(toJson(value), fieldName);
    }

    private void mergeObject(ObjectNode target, JsonNode source, String nestedField) {
        JsonNode sourceObject = nestedField == null ? source : source.path(nestedField);
        if (!sourceObject.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = sourceObject.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!"reportQueryId".equals(field.getKey())) {
                target.set(field.getKey(), field.getValue());
            }
        }
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

    private UUID firstUuid(JsonNode first, JsonNode second, String fieldName) {
        UUID value = optionalUuid(first, fieldName);
        return value == null ? optionalUuid(second, fieldName) : value;
    }

    private UUID optionalUuid(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !hasText(value.asText(null))) {
            return null;
        }
        return parseUuid(value.asText(), fieldName + " must be a UUID");
    }

    private ReportQueryCatalogEntry entry(UUID queryId) {
        return reportQueryCatalogRepository.findById(queryId).orElseThrow(() -> notFound("Report query not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private UUID normalizeProjectId(UUID workspaceId, String visibility, UUID projectId) {
        if (!"project".equals(visibility)) {
            if (projectId != null) {
                throw badRequest("projectId is only valid for project report queries");
            }
            return null;
        }
        if (projectId == null) {
            throw badRequest("projectId is required for project report queries");
        }
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> badRequest("Project not found in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("Project not found in this workspace");
        }
        return projectId;
    }

    private UUID normalizeTeamId(UUID workspaceId, String visibility, UUID teamId) {
        if (!"team".equals(visibility)) {
            if (teamId != null) {
                throw badRequest("teamId is only valid for team report queries");
            }
            return null;
        }
        if (teamId == null) {
            throw badRequest("teamId is required for team report queries");
        }
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, workspaceId)
                .orElseThrow(() -> badRequest("Team not found in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw badRequest("Team is not active");
        }
        return teamId;
    }

    private void requireReadable(UUID actorId, ReportQueryCatalogEntry entry) {
        if (!canRead(actorId, entry, canUseWorkspace(actorId, entry.getWorkspaceId(), "report.manage"))) {
            throw notFound("Report query not found");
        }
    }

    private boolean canRead(UUID actorId, ReportQueryCatalogEntry entry, boolean canManageWorkspaceReports) {
        if (canManageWorkspaceReports) {
            return true;
        }
        if (actorId.equals(entry.getOwnerId())) {
            if ("project".equals(entry.getVisibility()) && entry.getProjectId() != null) {
                return canUseProject(actorId, entry.getProjectId(), "report.read")
                        || canUseWorkspace(actorId, entry.getWorkspaceId(), "report.read");
            }
            return canUseWorkspace(actorId, entry.getWorkspaceId(), "report.read");
        }
        if ("workspace".equals(entry.getVisibility()) || "public".equals(entry.getVisibility())) {
            return canUseWorkspace(actorId, entry.getWorkspaceId(), "report.read");
        }
        if ("project".equals(entry.getVisibility()) && entry.getProjectId() != null) {
            return canUseProject(actorId, entry.getProjectId(), "report.read");
        }
        return "team".equals(entry.getVisibility())
                && entry.getTeamId() != null
                && canUseWorkspace(actorId, entry.getWorkspaceId(), "report.read")
                && teamMembershipRepository.existsByTeamIdAndUserIdAndLeftAtIsNull(entry.getTeamId(), actorId);
    }

    private void requireSavedFilterReadable(UUID actorId, SavedFilter savedFilter) {
        boolean canManageWorkspaceReports = canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.manage");
        if (canManageWorkspaceReports) {
            return;
        }
        if (actorId.equals(savedFilter.getOwnerId())) {
            if ("project".equals(savedFilter.getVisibility()) && savedFilter.getProjectId() != null) {
                if (canUseProject(actorId, savedFilter.getProjectId(), "report.read")
                        || canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read")) {
                    return;
                }
            } else if (canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read")) {
                return;
            }
        }
        if ("workspace".equals(savedFilter.getVisibility()) || "public".equals(savedFilter.getVisibility())) {
            if (canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read")) {
                return;
            }
        }
        if ("project".equals(savedFilter.getVisibility())
                && savedFilter.getProjectId() != null
                && canUseProject(actorId, savedFilter.getProjectId(), "report.read")) {
            return;
        }
        if ("team".equals(savedFilter.getVisibility())
                && savedFilter.getTeamId() != null
                && canUseWorkspace(actorId, savedFilter.getWorkspaceId(), "report.read")
                && teamMembershipRepository.existsByTeamIdAndUserIdAndLeftAtIsNull(savedFilter.getTeamId(), actorId)) {
            return;
        }
        throw notFound("Saved filter not found");
    }

    private void requireWritable(UUID actorId, ReportQueryCatalogEntry entry) {
        requireManage(actorId, entry.getWorkspaceId(), entry.getVisibility(), entry.getProjectId());
    }

    private void requireManage(UUID actorId, UUID workspaceId, String visibility, UUID projectId) {
        if ("project".equals(visibility)) {
            permissionService.requireProjectPermission(actorId, required(projectId, "projectId"), "report.manage");
            return;
        }
        permissionService.requireWorkspacePermission(actorId, workspaceId, "report.manage");
    }

    private boolean canUseWorkspace(UUID actorId, UUID workspaceId, String permissionKey) {
        return permissionService.canUseWorkspace(actorId, workspaceId, permissionKey);
    }

    private boolean canUseProject(UUID actorId, UUID projectId, String permissionKey) {
        return permissionService.canUseProject(actorId, projectId, permissionKey);
    }

    private void recordEvent(ReportQueryCatalogEntry entry, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("reportQueryId", entry.getId().toString())
                .put("queryKey", entry.getQueryKey())
                .put("queryType", entry.getQueryType())
                .put("visibility", entry.getVisibility())
                .put("actorUserId", actorId.toString());
        if (entry.getProjectId() != null) {
            payload.put("projectId", entry.getProjectId().toString());
        }
        if (entry.getTeamId() != null) {
            payload.put("teamId", entry.getTeamId().toString());
        }
        domainEventService.record(entry.getWorkspaceId(), "report_query", entry.getId(), eventType, payload);
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

    private String normalizeVisibility(String visibility) {
        String normalized = hasText(visibility) ? visibility.trim().toLowerCase() : "private";
        if (!List.of("private", "team", "project", "workspace", "public").contains(normalized)) {
            throw badRequest("visibility must be private, team, project, workspace, or public");
        }
        return normalized;
    }

    private String normalizeQueryType(String queryType) {
        String normalized = normalizeKey(requiredText(queryType, "queryType"));
        if (!List.of(
                "project_dashboard_summary",
                "workspace_dashboard_summary",
                "portfolio_dashboard_summary",
                "program_dashboard_summary",
                "snapshot_series",
                "iteration_report"
        ).contains(normalized)) {
            throw badRequest("queryType must be a governed reporting query type");
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        return requiredText(value, "key").toLowerCase().replace('-', '_').replace(' ', '_');
    }

    private UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw badRequest(message);
        }
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
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
}
