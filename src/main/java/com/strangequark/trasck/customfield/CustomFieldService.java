package com.strangequark.trasck.customfield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workitem.WorkItemType;
import com.strangequark.trasck.workitem.WorkItemTypeRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomFieldService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,119}");
    private static final List<String> FIELD_TYPES = List.of(
            "text",
            "textarea",
            "number",
            "integer",
            "boolean",
            "date",
            "datetime",
            "single_select",
            "multi_select",
            "user",
            "url",
            "json"
    );
    private static final List<String> SCREEN_OPERATIONS = List.of("create", "edit", "view");

    private final ObjectMapper objectMapper;
    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldContextRepository customFieldContextRepository;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final ScreenRepository screenRepository;
    private final ScreenFieldRepository screenFieldRepository;
    private final ScreenAssignmentRepository screenAssignmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public CustomFieldService(
            ObjectMapper objectMapper,
            CustomFieldRepository customFieldRepository,
            CustomFieldContextRepository customFieldContextRepository,
            CustomFieldValueRepository customFieldValueRepository,
            ScreenRepository screenRepository,
            ScreenFieldRepository screenFieldRepository,
            ScreenAssignmentRepository screenAssignmentRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            WorkItemRepository workItemRepository,
            WorkItemTypeRepository workItemTypeRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.customFieldRepository = customFieldRepository;
        this.customFieldContextRepository = customFieldContextRepository;
        this.customFieldValueRepository = customFieldValueRepository;
        this.screenRepository = screenRepository;
        this.screenFieldRepository = screenFieldRepository;
        this.screenAssignmentRepository = screenAssignmentRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.workItemTypeRepository = workItemTypeRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<CustomFieldResponse> listCustomFields(UUID workspaceId, boolean includeArchived) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return customFieldRepository.findByWorkspaceIdOrderByKeyAsc(workspaceId).stream()
                .filter(field -> includeArchived || !Boolean.TRUE.equals(field.getArchived()))
                .map(CustomFieldResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomFieldResponse getCustomField(UUID customFieldId) {
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.read");
        return CustomFieldResponse.from(field);
    }

    @Transactional
    public CustomFieldResponse createCustomField(UUID workspaceId, CustomFieldRequest request) {
        CustomFieldRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        CustomField field = new CustomField();
        field.setWorkspaceId(workspaceId);
        applyCustomFieldRequest(field, createRequest, true);
        OffsetDateTime now = OffsetDateTime.now();
        field.setCreatedAt(now);
        field.setUpdatedAt(now);
        CustomField saved = customFieldRepository.save(field);
        recordFieldEvent(saved, "custom_field.created", actorId);
        return CustomFieldResponse.from(saved);
    }

    @Transactional
    public CustomFieldResponse updateCustomField(UUID customFieldId, CustomFieldRequest request) {
        CustomFieldRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.admin");
        applyCustomFieldRequest(field, updateRequest, false);
        field.setUpdatedAt(OffsetDateTime.now());
        field.setVersion(field.getVersion() == null ? 1 : field.getVersion() + 1);
        CustomField saved = customFieldRepository.save(field);
        recordFieldEvent(saved, "custom_field.updated", actorId);
        return CustomFieldResponse.from(saved);
    }

    @Transactional
    public void archiveCustomField(UUID customFieldId) {
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.admin");
        field.setArchived(true);
        field.setUpdatedAt(OffsetDateTime.now());
        field.setVersion(field.getVersion() == null ? 1 : field.getVersion() + 1);
        customFieldRepository.save(field);
        recordFieldEvent(field, "custom_field.archived", actorId);
    }

    @Transactional(readOnly = true)
    public List<CustomFieldContextResponse> listContexts(UUID customFieldId) {
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.read");
        return customFieldContextRepository.findByCustomFieldIdOrderByProjectIdAscWorkItemTypeIdAsc(field.getId()).stream()
                .map(CustomFieldContextResponse::from)
                .toList();
    }

    @Transactional
    public CustomFieldContextResponse createContext(UUID customFieldId, CustomFieldContextRequest request) {
        CustomFieldContextRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.admin");
        CustomFieldContext context = new CustomFieldContext();
        context.setCustomFieldId(field.getId());
        applyContextRequest(field.getWorkspaceId(), context, createRequest, true);
        CustomFieldContext saved = customFieldContextRepository.save(context);
        recordFieldEvent(field, "custom_field.context_created", actorId);
        return CustomFieldContextResponse.from(saved);
    }

    @Transactional
    public CustomFieldContextResponse updateContext(UUID customFieldId, UUID contextId, CustomFieldContextRequest request) {
        CustomFieldContextRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.admin");
        CustomFieldContext context = customFieldContextRepository.findByIdAndCustomFieldId(contextId, field.getId())
                .orElseThrow(() -> notFound("Custom field context not found"));
        applyContextRequest(field.getWorkspaceId(), context, updateRequest, false);
        CustomFieldContext saved = customFieldContextRepository.save(context);
        recordFieldEvent(field, "custom_field.context_updated", actorId);
        return CustomFieldContextResponse.from(saved);
    }

    @Transactional
    public void deleteContext(UUID customFieldId, UUID contextId) {
        UUID actorId = currentUserService.requireUserId();
        CustomField field = customField(customFieldId);
        activeWorkspace(field.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, field.getWorkspaceId(), "workspace.admin");
        CustomFieldContext context = customFieldContextRepository.findByIdAndCustomFieldId(contextId, field.getId())
                .orElseThrow(() -> notFound("Custom field context not found"));
        customFieldContextRepository.delete(context);
        recordFieldEvent(field, "custom_field.context_deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<CustomFieldValueResponse> listValues(UUID workItemId) {
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.read");
        Map<UUID, CustomField> fieldsById = customFieldRepository.findByWorkspaceIdOrderByKeyAsc(item.getWorkspaceId()).stream()
                .collect(Collectors.toMap(CustomField::getId, field -> field));
        return customFieldValueRepository.findByWorkItemIdOrderByCustomFieldIdAsc(item.getId()).stream()
                .filter(value -> fieldsById.containsKey(value.getCustomFieldId()))
                .map(value -> CustomFieldValueResponse.from(value, fieldsById.get(value.getCustomFieldId())))
                .toList();
    }

    @Transactional
    public CustomFieldValueResponse setValue(UUID workItemId, UUID customFieldId, CustomFieldValueRequest request) {
        CustomFieldValueRequest valueRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.update");
        CustomField field = activeFieldForItem(item, customFieldId);
        JsonNode value = toJsonNullable(valueRequest.value());
        assertValueAllowed(field, item, value);
        CustomFieldValue fieldValue = customFieldValueRepository.findByWorkItemIdAndCustomFieldId(item.getId(), field.getId())
                .orElseGet(() -> {
                    CustomFieldValue created = new CustomFieldValue();
                    created.setWorkItemId(item.getId());
                    created.setCustomFieldId(field.getId());
                    return created;
                });
        fieldValue.setValue(value);
        fieldValue.setUpdatedAt(OffsetDateTime.now());
        CustomFieldValue saved = customFieldValueRepository.save(fieldValue);
        recordWorkItemFieldEvent(item, field, "work_item.custom_field_value_updated", actorId);
        return CustomFieldValueResponse.from(saved, field);
    }

    @Transactional
    public void deleteValue(UUID workItemId, UUID customFieldId) {
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.update");
        CustomField field = activeFieldForItem(item, customFieldId);
        if (isRequiredForItem(field, item)) {
            throw badRequest("Cannot clear a required custom field value");
        }
        CustomFieldValue value = customFieldValueRepository.findByWorkItemIdAndCustomFieldId(item.getId(), field.getId())
                .orElseThrow(() -> notFound("Custom field value not found"));
        customFieldValueRepository.delete(value);
        recordWorkItemFieldEvent(item, field, "work_item.custom_field_value_deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<ScreenResponse> listScreens(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return screenRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(this::screenResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScreenResponse getScreen(UUID screenId) {
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.read");
        return screenResponse(screen);
    }

    @Transactional
    public ScreenResponse createScreen(UUID workspaceId, ScreenRequest request) {
        ScreenRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        Screen screen = new Screen();
        screen.setWorkspaceId(workspaceId);
        applyScreenRequest(screen, createRequest, true);
        Screen saved = screenRepository.save(screen);
        recordScreenEvent(saved, "screen.created", actorId);
        return screenResponse(saved);
    }

    @Transactional
    public ScreenResponse updateScreen(UUID screenId, ScreenRequest request) {
        ScreenRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        applyScreenRequest(screen, updateRequest, false);
        Screen saved = screenRepository.save(screen);
        recordScreenEvent(saved, "screen.updated", actorId);
        return screenResponse(saved);
    }

    @Transactional
    public void deleteScreen(UUID screenId) {
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        screenRepository.delete(screen);
        recordScreenEvent(screen, "screen.deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<ScreenFieldResponse> listScreenFields(UUID screenId) {
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.read");
        return screenFieldRepository.findByScreenIdOrderByPositionAsc(screen.getId()).stream()
                .map(ScreenFieldResponse::from)
                .toList();
    }

    @Transactional
    public ScreenFieldResponse addScreenField(UUID screenId, ScreenFieldRequest request) {
        ScreenFieldRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        ScreenField field = new ScreenField();
        field.setScreenId(screen.getId());
        applyScreenFieldRequest(screen.getWorkspaceId(), field, createRequest, true);
        ScreenField saved = screenFieldRepository.save(field);
        recordScreenEvent(screen, "screen.field_created", actorId);
        return ScreenFieldResponse.from(saved);
    }

    @Transactional
    public ScreenFieldResponse updateScreenField(UUID screenId, UUID screenFieldId, ScreenFieldRequest request) {
        ScreenFieldRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        ScreenField field = screenFieldRepository.findByIdAndScreenId(screenFieldId, screen.getId())
                .orElseThrow(() -> notFound("Screen field not found"));
        applyScreenFieldRequest(screen.getWorkspaceId(), field, updateRequest, false);
        ScreenField saved = screenFieldRepository.save(field);
        recordScreenEvent(screen, "screen.field_updated", actorId);
        return ScreenFieldResponse.from(saved);
    }

    @Transactional
    public void deleteScreenField(UUID screenId, UUID screenFieldId) {
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        ScreenField field = screenFieldRepository.findByIdAndScreenId(screenFieldId, screen.getId())
                .orElseThrow(() -> notFound("Screen field not found"));
        screenFieldRepository.delete(field);
        recordScreenEvent(screen, "screen.field_deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<ScreenAssignmentResponse> listScreenAssignments(UUID screenId) {
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.read");
        return screenAssignmentRepository.findByScreenIdOrderByPriorityAsc(screen.getId()).stream()
                .map(ScreenAssignmentResponse::from)
                .toList();
    }

    @Transactional
    public ScreenAssignmentResponse addScreenAssignment(UUID screenId, ScreenAssignmentRequest request) {
        ScreenAssignmentRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        ScreenAssignment assignment = new ScreenAssignment();
        assignment.setScreenId(screen.getId());
        applyScreenAssignmentRequest(screen.getWorkspaceId(), assignment, createRequest, true);
        ScreenAssignment saved = screenAssignmentRepository.save(assignment);
        recordScreenEvent(screen, "screen.assignment_created", actorId);
        return ScreenAssignmentResponse.from(saved);
    }

    @Transactional
    public ScreenAssignmentResponse updateScreenAssignment(UUID screenId, UUID assignmentId, ScreenAssignmentRequest request) {
        ScreenAssignmentRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        ScreenAssignment assignment = screenAssignmentRepository.findByIdAndScreenId(assignmentId, screen.getId())
                .orElseThrow(() -> notFound("Screen assignment not found"));
        applyScreenAssignmentRequest(screen.getWorkspaceId(), assignment, updateRequest, false);
        ScreenAssignment saved = screenAssignmentRepository.save(assignment);
        recordScreenEvent(screen, "screen.assignment_updated", actorId);
        return ScreenAssignmentResponse.from(saved);
    }

    @Transactional
    public void deleteScreenAssignment(UUID screenId, UUID assignmentId) {
        UUID actorId = currentUserService.requireUserId();
        Screen screen = screen(screenId);
        activeWorkspace(screen.getWorkspaceId());
        permissionService.requireWorkspacePermission(actorId, screen.getWorkspaceId(), "workspace.admin");
        ScreenAssignment assignment = screenAssignmentRepository.findByIdAndScreenId(assignmentId, screen.getId())
                .orElseThrow(() -> notFound("Screen assignment not found"));
        screenAssignmentRepository.delete(assignment);
        recordScreenEvent(screen, "screen.assignment_deleted", actorId);
    }

    public UUID resolveSearchableFieldIdForProject(Project project, String customFieldKey) {
        CustomField field = customFieldRepository.findByWorkspaceIdAndKeyIgnoreCase(project.getWorkspaceId(), requiredText(customFieldKey, "customFieldKey"))
                .orElseThrow(() -> badRequest("Searchable custom field not found"));
        if (Boolean.TRUE.equals(field.getArchived()) || !Boolean.TRUE.equals(field.getSearchable())) {
            throw badRequest("Custom field is not searchable");
        }
        List<CustomFieldContext> contexts = customFieldContextRepository.findByCustomFieldIdOrderByProjectIdAscWorkItemTypeIdAsc(field.getId());
        boolean applies = contexts.isEmpty() || contexts.stream()
                .anyMatch(context -> context.getProjectId() == null || project.getId().equals(context.getProjectId()));
        if (!applies) {
            throw badRequest("Custom field does not apply to this project");
        }
        return field.getId();
    }

    private void applyCustomFieldRequest(CustomField field, CustomFieldRequest request, boolean create) {
        if (create || hasText(request.name())) {
            field.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.key())) {
            String key = normalizeKey(requiredText(request.key(), "key"));
            if (!key.equalsIgnoreCase(field.getKey()) && customFieldRepository.existsByWorkspaceIdAndKeyIgnoreCase(field.getWorkspaceId(), key)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Custom field key already exists in this workspace");
            }
            field.setKey(key);
        }
        if (create || hasText(request.fieldType())) {
            field.setFieldType(normalizeFieldType(requiredText(request.fieldType(), "fieldType")));
        }
        if (create || request.options() != null) {
            field.setOptions(toJsonObject(request.options()));
        }
        if (create) {
            field.setSearchable(Boolean.TRUE.equals(request.searchable()));
            field.setArchived(Boolean.TRUE.equals(request.archived()));
        } else {
            if (request.searchable() != null) {
                field.setSearchable(request.searchable());
            }
            if (request.archived() != null) {
                field.setArchived(request.archived());
            }
        }
    }

    private void applyContextRequest(UUID workspaceId, CustomFieldContext context, CustomFieldContextRequest request, boolean create) {
        if (create || request.projectId() != null) {
            context.setProjectId(request.projectId() == null ? null : validateProject(workspaceId, request.projectId()).getId());
        }
        if (create || request.workItemTypeId() != null) {
            context.setWorkItemTypeId(request.workItemTypeId() == null ? null : validateWorkItemType(workspaceId, request.workItemTypeId()).getId());
        }
        if (create) {
            context.setRequired(Boolean.TRUE.equals(request.required()));
            context.setDefaultValue(toJsonNullable(request.defaultValue()));
            context.setValidationConfig(toJsonObject(request.validationConfig()));
        } else {
            if (request.required() != null) {
                context.setRequired(request.required());
            }
            if (request.defaultValue() != null) {
                context.setDefaultValue(toJsonNullable(request.defaultValue()));
            }
            if (request.validationConfig() != null) {
                context.setValidationConfig(toJsonObject(request.validationConfig()));
            }
        }
    }

    private void applyScreenRequest(Screen screen, ScreenRequest request, boolean create) {
        if (create || hasText(request.name())) {
            screen.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.screenType())) {
            screen.setScreenType(requiredText(request.screenType(), "screenType").toLowerCase());
        }
        if (create || request.config() != null) {
            screen.setConfig(toJsonObject(request.config()));
        }
    }

    private void applyScreenFieldRequest(UUID workspaceId, ScreenField field, ScreenFieldRequest request, boolean create) {
        boolean settingCustomField = request.customFieldId() != null;
        boolean settingSystemField = hasText(request.systemFieldKey());
        if (create && settingCustomField == settingSystemField) {
            throw badRequest("Exactly one of customFieldId or systemFieldKey is required");
        }
        if (settingCustomField) {
            CustomField customField = customField(request.customFieldId());
            if (!workspaceId.equals(customField.getWorkspaceId()) || Boolean.TRUE.equals(customField.getArchived())) {
                throw badRequest("Custom field does not belong to this workspace");
            }
            field.setCustomFieldId(customField.getId());
            field.setSystemFieldKey(null);
        } else if (settingSystemField) {
            field.setSystemFieldKey(requiredText(request.systemFieldKey(), "systemFieldKey"));
            field.setCustomFieldId(null);
        }
        if (request.position() != null) {
            field.setPosition(Math.max(0, request.position()));
        } else if (create) {
            field.setPosition(0);
        }
        if (request.required() != null) {
            field.setRequired(request.required());
        } else if (create) {
            field.setRequired(false);
        }
    }

    private void applyScreenAssignmentRequest(UUID workspaceId, ScreenAssignment assignment, ScreenAssignmentRequest request, boolean create) {
        if (create || request.projectId() != null) {
            assignment.setProjectId(request.projectId() == null ? null : validateProject(workspaceId, request.projectId()).getId());
        }
        if (create || request.workItemTypeId() != null) {
            assignment.setWorkItemTypeId(request.workItemTypeId() == null ? null : validateWorkItemType(workspaceId, request.workItemTypeId()).getId());
        }
        if (create || hasText(request.operation())) {
            String operation = requiredText(request.operation(), "operation").toLowerCase();
            if (!SCREEN_OPERATIONS.contains(operation)) {
                throw badRequest("operation must be create, edit, or view");
            }
            assignment.setOperation(operation);
        }
        if (request.priority() != null) {
            assignment.setPriority(Math.max(0, request.priority()));
        } else if (create) {
            assignment.setPriority(0);
        }
    }

    private CustomField activeFieldForItem(WorkItem item, UUID customFieldId) {
        CustomField field = customField(customFieldId);
        if (!item.getWorkspaceId().equals(field.getWorkspaceId()) || Boolean.TRUE.equals(field.getArchived())) {
            throw badRequest("Custom field does not belong to this work item workspace");
        }
        assertFieldAppliesToItem(field, item);
        return field;
    }

    private void assertValueAllowed(CustomField field, WorkItem item, JsonNode value) {
        if (isRequiredForItem(field, item) && (value == null || value.isNull())) {
            throw badRequest("value is required for this custom field");
        }
        if (value == null || value.isNull()) {
            return;
        }
        validateValueType(field, value);
    }

    private void assertFieldAppliesToItem(CustomField field, WorkItem item) {
        List<CustomFieldContext> contexts = customFieldContextRepository.findByCustomFieldIdOrderByProjectIdAscWorkItemTypeIdAsc(field.getId());
        if (contexts.isEmpty()) {
            return;
        }
        if (contexts.stream().noneMatch(context -> contextApplies(context, item))) {
            throw badRequest("Custom field does not apply to this work item");
        }
    }

    private boolean isRequiredForItem(CustomField field, WorkItem item) {
        return customFieldContextRepository.findByCustomFieldIdOrderByProjectIdAscWorkItemTypeIdAsc(field.getId()).stream()
                .filter(context -> contextApplies(context, item))
                .anyMatch(context -> Boolean.TRUE.equals(context.getRequired()));
    }

    private boolean contextApplies(CustomFieldContext context, WorkItem item) {
        return (context.getProjectId() == null || context.getProjectId().equals(item.getProjectId()))
                && (context.getWorkItemTypeId() == null || context.getWorkItemTypeId().equals(item.getTypeId()));
    }

    private void validateValueType(CustomField field, JsonNode value) {
        switch (field.getFieldType()) {
            case "text", "textarea", "single_select", "user", "url", "date", "datetime" -> {
                if (!value.isTextual()) {
                    throw badRequest("value must be a string for " + field.getFieldType() + " custom fields");
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    throw badRequest("value must be a number");
                }
            }
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    throw badRequest("value must be an integer");
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) {
                    throw badRequest("value must be a boolean");
                }
            }
            case "multi_select" -> {
                if (!value.isArray()) {
                    throw badRequest("value must be an array for multi_select custom fields");
                }
                value.forEach(child -> {
                    if (!child.isTextual()) {
                        throw badRequest("multi_select values must be strings");
                    }
                });
            }
            case "json" -> {
            }
            default -> throw badRequest("Unsupported custom field type");
        }
    }

    private ScreenResponse screenResponse(Screen screen) {
        return ScreenResponse.from(
                screen,
                screenFieldRepository.findByScreenIdOrderByPositionAsc(screen.getId()),
                screenAssignmentRepository.findByScreenIdOrderByPriorityAsc(screen.getId())
        );
    }

    private CustomField customField(UUID customFieldId) {
        return customFieldRepository.findById(customFieldId).orElseThrow(() -> notFound("Custom field not found"));
    }

    private Screen screen(UUID screenId) {
        return screenRepository.findById(screenId).orElseThrow(() -> notFound("Screen not found"));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project validateProject(UUID workspaceId, UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> badRequest("Project not found in this workspace"));
        if (!workspaceId.equals(project.getWorkspaceId()) || !"active".equals(project.getStatus())) {
            throw badRequest("Project not found in this workspace");
        }
        return project;
    }

    private WorkItem activeWorkItem(UUID workItemId) {
        return workItemRepository.findByIdAndDeletedAtIsNull(workItemId).orElseThrow(() -> notFound("Work item not found"));
    }

    private WorkItemType validateWorkItemType(UUID workspaceId, UUID workItemTypeId) {
        WorkItemType type = workItemTypeRepository.findById(workItemTypeId).orElseThrow(() -> badRequest("Work item type not found in this workspace"));
        if (!workspaceId.equals(type.getWorkspaceId()) || !Boolean.TRUE.equals(type.getEnabled())) {
            throw badRequest("Work item type not found in this workspace");
        }
        return type;
    }

    private JsonNode toJsonNullable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private JsonNode toJsonObject(Object value) {
        JsonNode json = toJsonNullable(value);
        if (json == null || json.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!json.isObject()) {
            throw badRequest("JSON value must be an object");
        }
        return json;
    }

    private String normalizeKey(String key) {
        String normalized = key.toLowerCase();
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw badRequest("key must start with a lowercase letter or number and contain only lowercase letters, numbers, hyphens, or underscores");
        }
        return normalized;
    }

    private String normalizeFieldType(String fieldType) {
        String normalized = fieldType.toLowerCase();
        if (!FIELD_TYPES.contains(normalized)) {
            throw badRequest("fieldType must be one of " + String.join(", ", FIELD_TYPES));
        }
        return normalized;
    }

    private void recordFieldEvent(CustomField field, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("customFieldId", field.getId().toString())
                .put("customFieldKey", field.getKey())
                .put("actorUserId", actorId.toString());
        domainEventService.record(field.getWorkspaceId(), "custom_field", field.getId(), eventType, payload);
    }

    private void recordScreenEvent(Screen screen, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("screenId", screen.getId().toString())
                .put("screenName", screen.getName())
                .put("actorUserId", actorId.toString());
        domainEventService.record(screen.getWorkspaceId(), "screen", screen.getId(), eventType, payload);
    }

    private void recordWorkItemFieldEvent(WorkItem item, CustomField field, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("projectId", item.getProjectId().toString())
                .put("customFieldId", field.getId().toString())
                .put("customFieldKey", field.getKey())
                .put("actorUserId", actorId.toString());
        domainEventService.record(item.getWorkspaceId(), "work_item", item.getId(), eventType, payload);
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
