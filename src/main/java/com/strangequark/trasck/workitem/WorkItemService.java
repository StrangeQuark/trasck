package com.strangequark.trasck.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.reporting.WorkItemAssignmentHistory;
import com.strangequark.trasck.reporting.WorkItemAssignmentHistoryRepository;
import com.strangequark.trasck.reporting.WorkItemEstimateHistory;
import com.strangequark.trasck.reporting.WorkItemEstimateHistoryRepository;
import com.strangequark.trasck.reporting.WorkItemStatusHistory;
import com.strangequark.trasck.reporting.WorkItemStatusHistoryRepository;
import com.strangequark.trasck.team.ProjectTeamRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workflow.WorkflowAssignment;
import com.strangequark.trasck.workflow.WorkflowAssignmentRepository;
import com.strangequark.trasck.workflow.WorkflowStatus;
import com.strangequark.trasck.workflow.WorkflowStatusRepository;
import com.strangequark.trasck.workflow.WorkflowTransition;
import com.strangequark.trasck.workflow.WorkflowTransitionAction;
import com.strangequark.trasck.workflow.WorkflowTransitionActionRepository;
import com.strangequark.trasck.workflow.WorkflowTransitionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkItemService {

    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final ProjectWorkItemTypeRepository projectWorkItemTypeRepository;
    private final WorkItemTypeRuleRepository workItemTypeRuleRepository;
    private final WorkItemClosureRepository workItemClosureRepository;
    private final PriorityRepository priorityRepository;
    private final TeamRepository teamRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final WorkflowAssignmentRepository workflowAssignmentRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowTransitionActionRepository workflowTransitionActionRepository;
    private final WorkItemStatusHistoryRepository workItemStatusHistoryRepository;
    private final WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository;
    private final WorkItemEstimateHistoryRepository workItemEstimateHistoryRepository;
    private final WorkItemSequenceService workItemSequenceService;
    private final WorkItemRankService workItemRankService;
    private final DomainEventService domainEventService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final JdbcTemplate jdbcTemplate;

    public WorkItemService(
            ObjectMapper objectMapper,
            ProjectRepository projectRepository,
            WorkItemRepository workItemRepository,
            WorkItemTypeRepository workItemTypeRepository,
            ProjectWorkItemTypeRepository projectWorkItemTypeRepository,
            WorkItemTypeRuleRepository workItemTypeRuleRepository,
            WorkItemClosureRepository workItemClosureRepository,
            PriorityRepository priorityRepository,
            TeamRepository teamRepository,
            ProjectTeamRepository projectTeamRepository,
            WorkflowAssignmentRepository workflowAssignmentRepository,
            WorkflowStatusRepository workflowStatusRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            WorkflowTransitionActionRepository workflowTransitionActionRepository,
            WorkItemStatusHistoryRepository workItemStatusHistoryRepository,
            WorkItemAssignmentHistoryRepository workItemAssignmentHistoryRepository,
            WorkItemEstimateHistoryRepository workItemEstimateHistoryRepository,
            WorkItemSequenceService workItemSequenceService,
            WorkItemRankService workItemRankService,
            DomainEventService domainEventService,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectMapper = objectMapper;
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.workItemTypeRepository = workItemTypeRepository;
        this.projectWorkItemTypeRepository = projectWorkItemTypeRepository;
        this.workItemTypeRuleRepository = workItemTypeRuleRepository;
        this.workItemClosureRepository = workItemClosureRepository;
        this.priorityRepository = priorityRepository;
        this.teamRepository = teamRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.workflowAssignmentRepository = workflowAssignmentRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.workflowTransitionActionRepository = workflowTransitionActionRepository;
        this.workItemStatusHistoryRepository = workItemStatusHistoryRepository;
        this.workItemAssignmentHistoryRepository = workItemAssignmentHistoryRepository;
        this.workItemEstimateHistoryRepository = workItemEstimateHistoryRepository;
        this.workItemSequenceService = workItemSequenceService;
        this.workItemRankService = workItemRankService;
        this.domainEventService = domainEventService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public WorkItemResponse create(UUID projectId, WorkItemCreateRequest request) {
        WorkItemCreateRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "work_item.create");
        WorkItemType type = resolveType(project.getWorkspaceId(), createRequest.typeId(), createRequest.typeKey());
        assertTypeEnabledForProject(project.getId(), type.getId());
        WorkItem parent = resolveParent(project, createRequest.parentId());
        validateParentType(project.getWorkspaceId(), parent, type);
        WorkflowAssignment workflowAssignment = workflowAssignment(project.getId(), type.getId());
        WorkflowStatus status = resolveStatus(workflowAssignment.getWorkflowId(), createRequest.statusId(), createRequest.statusKey());

        long projectSequence = workItemSequenceService.nextProjectSequence(project.getWorkspaceId(), project.getId());
        long workspaceSequence = workItemSequenceService.nextWorkspaceSequence(project.getWorkspaceId());
        UUID reporterId = firstNonNull(createRequest.reporterId(), actorId);

        WorkItem item = new WorkItem();
        item.setWorkspaceId(project.getWorkspaceId());
        item.setProjectId(project.getId());
        item.setTypeId(type.getId());
        item.setParentId(parent == null ? null : parent.getId());
        item.setStatusId(status.getId());
        item.setPriorityId(resolvePriorityId(project.getWorkspaceId(), createRequest.priorityId(), createRequest.priorityKey()));
        item.setTeamId(resolveTeamId(project, createRequest.teamId()));
        item.setAssigneeId(createRequest.assigneeId());
        item.setReporterId(reporterId);
        item.setCreatedById(actorId);
        item.setUpdatedById(actorId);
        item.setKey(project.getKey() + "-" + projectSequence);
        item.setSequenceNumber(projectSequence);
        item.setWorkspaceSequenceNumber(workspaceSequence);
        item.setTitle(requiredText(createRequest.title(), "title"));
        item.setDescriptionMarkdown(createRequest.descriptionMarkdown());
        item.setDescriptionDocument(toJsonNode(createRequest.descriptionDocument()));
        item.setVisibility(normalizeVisibility(createRequest.visibility()));
        item.setEstimatePoints(createRequest.estimatePoints());
        item.setEstimateMinutes(createRequest.estimateMinutes());
        item.setRemainingMinutes(createRequest.remainingMinutes());
        item.setRank(workItemRankService.appendRank(project.getId()));
        item.setStartDate(createRequest.startDate());
        item.setDueDate(createRequest.dueDate());
        WorkItem saved = workItemRepository.saveAndFlush(item);
        rebuildClosure(project.getId());
        writeStatusHistory(saved.getId(), null, saved.getStatusId(), actorId);
        if (saved.getAssigneeId() != null) {
            writeAssignmentHistory(saved.getId(), null, saved.getAssigneeId(), actorId);
        }
        writeInitialEstimateHistory(saved, actorId);
        recordEvent(saved, "work_item.created", actorId);
        return WorkItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkItemResponse> listByProject(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        activeProject(projectId);
        permissionService.requireProjectPermission(actorId, projectId, "work_item.read");
        return workItemRepository.findByProjectIdAndDeletedAtIsNullOrderByRankAsc(projectId).stream()
                .map(WorkItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkItemResponse get(UUID workItemId) {
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.read");
        return WorkItemResponse.from(item);
    }

    @Transactional
    public WorkItemResponse update(UUID workItemId, WorkItemUpdateRequest request) {
        WorkItemUpdateRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        Project project = activeProject(item.getProjectId());
        permissionService.requireProjectPermission(actorId, project.getId(), "work_item.update");
        UUID oldParentId = item.getParentId();
        UUID oldTypeId = item.getTypeId();
        BigDecimal oldEstimatePoints = item.getEstimatePoints();
        Integer oldEstimateMinutes = item.getEstimateMinutes();
        Integer oldRemainingMinutes = item.getRemainingMinutes();

        if (updateRequest.typeId() != null || hasText(updateRequest.typeKey())) {
            WorkItemType type = resolveType(project.getWorkspaceId(), updateRequest.typeId(), updateRequest.typeKey());
            assertTypeEnabledForProject(project.getId(), type.getId());
            item.setTypeId(type.getId());
        }
        if (Boolean.TRUE.equals(updateRequest.clearParent())) {
            item.setParentId(null);
        } else if (updateRequest.parentId() != null) {
            WorkItem parent = resolveParent(project, updateRequest.parentId());
            if (workItemClosureRepository.existsByIdAncestorWorkItemIdAndIdDescendantWorkItemId(item.getId(), parent.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A work item cannot be moved under one of its descendants");
            }
            item.setParentId(parent.getId());
        }
        WorkItem parent = item.getParentId() == null ? null : resolveParent(project, item.getParentId());
        WorkItemType type = workItemTypeRepository.findById(item.getTypeId()).orElseThrow(() -> notFound("Work item type not found"));
        validateParentType(project.getWorkspaceId(), parent, type);
        validateExistingChildren(project.getWorkspaceId(), item, type);

        if (updateRequest.priorityId() != null || hasText(updateRequest.priorityKey())) {
            item.setPriorityId(resolvePriorityId(project.getWorkspaceId(), updateRequest.priorityId(), updateRequest.priorityKey()));
        }
        if (Boolean.TRUE.equals(updateRequest.clearTeam())) {
            item.setTeamId(null);
        } else if (updateRequest.teamId() != null) {
            item.setTeamId(resolveTeamId(project, updateRequest.teamId()));
        }
        if (hasText(updateRequest.title())) {
            item.setTitle(updateRequest.title().trim());
        }
        if (updateRequest.descriptionMarkdown() != null) {
            item.setDescriptionMarkdown(updateRequest.descriptionMarkdown());
        }
        if (updateRequest.descriptionDocument() != null) {
            item.setDescriptionDocument(toJsonNode(updateRequest.descriptionDocument()));
        }
        if (updateRequest.visibility() != null) {
            item.setVisibility(normalizeVisibility(updateRequest.visibility()));
        }
        if (updateRequest.estimatePoints() != null) {
            item.setEstimatePoints(updateRequest.estimatePoints());
        }
        if (updateRequest.estimateMinutes() != null) {
            item.setEstimateMinutes(updateRequest.estimateMinutes());
        }
        if (updateRequest.remainingMinutes() != null) {
            item.setRemainingMinutes(updateRequest.remainingMinutes());
        }
        if (updateRequest.startDate() != null) {
            item.setStartDate(updateRequest.startDate());
        }
        if (updateRequest.dueDate() != null) {
            item.setDueDate(updateRequest.dueDate());
        }
        item.setUpdatedById(actorId);
        WorkItem saved = workItemRepository.saveAndFlush(item);
        if (!same(oldParentId, saved.getParentId())) {
            rebuildClosure(project.getId());
            recordEvent(saved, "work_item.parent_changed", actorId);
        }
        if (!same(oldTypeId, saved.getTypeId())) {
            recordEvent(saved, "work_item.type_changed", actorId);
        }
        writeEstimateHistoryIfChanged(saved.getId(), "points", oldEstimatePoints, saved.getEstimatePoints(), actorId);
        writeEstimateHistoryIfChanged(saved.getId(), "minutes", toBigDecimal(oldEstimateMinutes), toBigDecimal(saved.getEstimateMinutes()), actorId);
        writeEstimateHistoryIfChanged(saved.getId(), "remaining_minutes", toBigDecimal(oldRemainingMinutes), toBigDecimal(saved.getRemainingMinutes()), actorId);
        recordEvent(saved, "work_item.updated", actorId);
        return WorkItemResponse.from(saved);
    }

    @Transactional
    public WorkItemResponse assign(UUID workItemId, WorkItemAssignRequest request) {
        WorkItemAssignRequest assignRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.update");
        UUID previousAssignee = item.getAssigneeId();
        if (same(previousAssignee, assignRequest.assigneeId())) {
            return WorkItemResponse.from(item);
        }
        item.setAssigneeId(assignRequest.assigneeId());
        item.setUpdatedById(actorId);
        WorkItem saved = workItemRepository.saveAndFlush(item);
        writeAssignmentHistory(saved.getId(), previousAssignee, saved.getAssigneeId(), actorId);
        recordEvent(saved, "work_item.assigned", actorId);
        return WorkItemResponse.from(saved);
    }

    @Transactional
    public WorkItemResponse rank(UUID workItemId, WorkItemRankRequest request) {
        WorkItemRankRequest rankRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.update");
        WorkItem previous = rankRequest.previousWorkItemId() == null ? null : activeWorkItemInProject(rankRequest.previousWorkItemId(), item.getProjectId());
        WorkItem next = rankRequest.nextWorkItemId() == null ? null : activeWorkItemInProject(rankRequest.nextWorkItemId(), item.getProjectId());
        if (previous != null && same(previous.getId(), item.getId()) || next != null && same(next.getId(), item.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A work item cannot be ranked relative to itself");
        }
        item.setRank(workItemRankService.between(previous == null ? null : previous.getRank(), next == null ? null : next.getRank()));
        item.setUpdatedById(actorId);
        WorkItem saved = workItemRepository.saveAndFlush(item);
        recordEvent(saved, "work_item.rank_changed", actorId);
        return WorkItemResponse.from(saved);
    }

    @Transactional
    public WorkItemResponse transition(UUID workItemId, WorkItemTransitionRequest request) {
        WorkItemTransitionRequest transitionRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        String transitionKey = requiredText(transitionRequest.transitionKey(), "transitionKey");
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.transition");
        WorkflowAssignment assignment = workflowAssignment(item.getProjectId(), item.getTypeId());
        WorkflowTransition transition = workflowTransitionRepository.findAllowedTransition(assignment.getWorkflowId(), transitionKey, item.getStatusId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow transition is not allowed from the current status"));
        WorkflowStatus toStatus = workflowStatusRepository.findById(transition.getToStatusId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transition target status not found"));
        UUID fromStatusId = item.getStatusId();
        item.setStatusId(toStatus.getId());
        item.setUpdatedById(actorId);
        applyTransitionActions(item, transition.getId());
        if (Boolean.TRUE.equals(toStatus.getTerminal())) {
            item.setResolvedAt(OffsetDateTime.now());
        }
        WorkItem saved = workItemRepository.saveAndFlush(item);
        writeStatusHistory(saved.getId(), fromStatusId, saved.getStatusId(), actorId);
        recordEvent(saved, "work_item.status_changed", actorId);
        return WorkItemResponse.from(saved);
    }

    @Transactional
    public void archive(UUID workItemId) {
        UUID actorId = currentUserService.requireUserId();
        WorkItem item = activeWorkItem(workItemId);
        permissionService.requireProjectPermission(actorId, item.getProjectId(), "work_item.delete");
        item.setDeletedAt(OffsetDateTime.now());
        item.setUpdatedById(actorId);
        WorkItem saved = workItemRepository.saveAndFlush(item);
        rebuildClosure(saved.getProjectId());
        recordEvent(saved, "work_item.archived", actorId);
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> notFound("Project not found"));
        if (project.getDeletedAt() != null || !"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        return project;
    }

    private WorkItem activeWorkItem(UUID workItemId) {
        return workItemRepository.findByIdAndDeletedAtIsNull(workItemId).orElseThrow(() -> notFound("Work item not found"));
    }

    private WorkItem activeWorkItemInProject(UUID workItemId, UUID projectId) {
        return workItemRepository.findActiveInProject(workItemId, projectId).orElseThrow(() -> notFound("Work item not found"));
    }

    private WorkItemType resolveType(UUID workspaceId, UUID typeId, String typeKey) {
        WorkItemType type;
        if (typeId != null) {
            type = workItemTypeRepository.findById(typeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type not found"));
        } else {
            type = workItemTypeRepository.findByWorkspaceIdAndKeyIgnoreCase(workspaceId, requiredText(typeKey, "typeKey"))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type not found"));
        }
        if (!same(workspaceId, type.getWorkspaceId()) || !Boolean.TRUE.equals(type.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type is not enabled in this workspace");
        }
        return type;
    }

    private void assertTypeEnabledForProject(UUID projectId, UUID typeId) {
        ProjectWorkItemType projectType = projectWorkItemTypeRepository.findByProjectIdAndWorkItemTypeId(projectId, typeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type is not enabled for this project"));
        if (!Boolean.TRUE.equals(projectType.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type is not enabled for this project");
        }
    }

    private WorkItem resolveParent(Project project, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        WorkItem parent = activeWorkItem(parentId);
        if (!same(project.getWorkspaceId(), parent.getWorkspaceId()) || !same(project.getId(), parent.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Structural parentage across projects is not allowed. Use work item links instead.");
        }
        return parent;
    }

    private void validateParentType(UUID workspaceId, WorkItem parent, WorkItemType childType) {
        if (parent == null) {
            return;
        }
        boolean allowed = workItemTypeRuleRepository.existsByWorkspaceIdAndParentTypeIdAndChildTypeIdAndEnabledTrue(
                workspaceId,
                parent.getTypeId(),
                childType.getId()
        );
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent work item type cannot contain this child work item type");
        }
    }

    private void validateExistingChildren(UUID workspaceId, WorkItem item, WorkItemType parentType) {
        for (WorkItem child : workItemRepository.findByParentIdAndDeletedAtIsNull(item.getId())) {
            boolean allowed = workItemTypeRuleRepository.existsByWorkspaceIdAndParentTypeIdAndChildTypeIdAndEnabledTrue(
                    workspaceId,
                    parentType.getId(),
                    child.getTypeId()
            );
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Changing this work item type would make an existing child invalid");
            }
        }
    }

    private WorkflowAssignment workflowAssignment(UUID projectId, UUID typeId) {
        return workflowAssignmentRepository.findByProjectIdAndWorkItemTypeId(projectId, typeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No workflow is assigned to this work item type"));
    }

    private WorkflowStatus resolveStatus(UUID workflowId, UUID statusId, String statusKey) {
        if (statusId != null) {
            WorkflowStatus status = workflowStatusRepository.findById(statusId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow status not found"));
            if (!same(workflowId, status.getWorkflowId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow status does not belong to the assigned workflow");
            }
            return status;
        }
        if (hasText(statusKey)) {
            return workflowStatusRepository.findByWorkflowIdAndKeyIgnoreCase(workflowId, statusKey)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow status not found"));
        }
        return workflowStatusRepository.findByWorkflowIdOrderBySortOrderAsc(workflowId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow has no statuses"));
    }

    private UUID resolvePriorityId(UUID workspaceId, UUID priorityId, String priorityKey) {
        if (priorityId != null) {
            Priority priority = priorityRepository.findById(priorityId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority not found"));
            if (!same(workspaceId, priority.getWorkspaceId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority does not belong to this workspace");
            }
            return priority.getId();
        }
        if (hasText(priorityKey)) {
            return priorityRepository.findByWorkspaceIdAndKeyIgnoreCase(workspaceId, priorityKey)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority not found"))
                    .getId();
        }
        return priorityRepository.findByWorkspaceIdAndIsDefaultTrue(workspaceId).map(Priority::getId).orElse(null);
    }

    private UUID resolveTeamId(Project project, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, project.getWorkspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team not found in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team is not active");
        }
        if (!projectTeamRepository.existsByIdProjectIdAndIdTeamId(project.getId(), team.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team is not assigned to this project");
        }
        return team.getId();
    }

    private void applyTransitionActions(WorkItem item, UUID transitionId) {
        for (WorkflowTransitionAction action : workflowTransitionActionRepository.findByTransitionIdAndEnabledTrueOrderByPositionAsc(transitionId)) {
            if ("set_resolution".equals(action.getActionType()) && action.getConfig() != null && action.getConfig().hasNonNull("resolutionId")) {
                item.setResolutionId(UUID.fromString(action.getConfig().get("resolutionId").asText()));
            }
        }
    }

    private void writeStatusHistory(UUID workItemId, UUID fromStatusId, UUID toStatusId, UUID actorId) {
        WorkItemStatusHistory history = new WorkItemStatusHistory();
        history.setWorkItemId(workItemId);
        history.setFromStatusId(fromStatusId);
        history.setToStatusId(toStatusId);
        history.setChangedById(actorId);
        workItemStatusHistoryRepository.save(history);
    }

    private void writeAssignmentHistory(UUID workItemId, UUID fromUserId, UUID toUserId, UUID actorId) {
        WorkItemAssignmentHistory history = new WorkItemAssignmentHistory();
        history.setWorkItemId(workItemId);
        history.setFromUserId(fromUserId);
        history.setToUserId(toUserId);
        history.setChangedById(actorId);
        workItemAssignmentHistoryRepository.save(history);
    }

    private void writeInitialEstimateHistory(WorkItem item, UUID actorId) {
        if (item.getEstimatePoints() != null) {
            writeEstimateHistory(item.getId(), "points", null, item.getEstimatePoints(), actorId);
        }
        if (item.getEstimateMinutes() != null) {
            writeEstimateHistory(item.getId(), "minutes", null, toBigDecimal(item.getEstimateMinutes()), actorId);
        }
        if (item.getRemainingMinutes() != null) {
            writeEstimateHistory(item.getId(), "remaining_minutes", null, toBigDecimal(item.getRemainingMinutes()), actorId);
        }
    }

    private void writeEstimateHistoryIfChanged(UUID workItemId, String estimateType, BigDecimal oldValue, BigDecimal newValue, UUID actorId) {
        if (!sameDecimal(oldValue, newValue)) {
            writeEstimateHistory(workItemId, estimateType, oldValue, newValue, actorId);
        }
    }

    private void writeEstimateHistory(UUID workItemId, String estimateType, BigDecimal oldValue, BigDecimal newValue, UUID actorId) {
        WorkItemEstimateHistory history = new WorkItemEstimateHistory();
        history.setWorkItemId(workItemId);
        history.setEstimateType(estimateType);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangedById(actorId);
        workItemEstimateHistoryRepository.save(history);
    }

    private void rebuildClosure(UUID projectId) {
        jdbcTemplate.update("""
                delete from work_item_closure
                where descendant_work_item_id in (
                    select id from work_items where project_id = ?
                )
                """, projectId);
        jdbcTemplate.update("""
                insert into work_item_closure (workspace_id, ancestor_work_item_id, descendant_work_item_id, depth)
                with recursive hierarchy as (
                    select workspace_id, id as ancestor_work_item_id, id as descendant_work_item_id, 0 as depth
                    from work_items
                    where project_id = ? and deleted_at is null
                    union all
                    select hierarchy.workspace_id,
                           hierarchy.ancestor_work_item_id,
                           child.id as descendant_work_item_id,
                           hierarchy.depth + 1
                    from hierarchy
                    join work_items child on child.parent_id = hierarchy.descendant_work_item_id
                    where child.project_id = ? and child.deleted_at is null
                )
                select workspace_id, ancestor_work_item_id, descendant_work_item_id, depth
                from hierarchy
                """, projectId, projectId);
    }

    private void recordEvent(WorkItem item, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey())
                .put("projectId", item.getProjectId().toString());
        if (actorId != null) {
            payload.put("actorUserId", actorId.toString());
        }
        domainEventService.record(item.getWorkspaceId(), "work_item", item.getId(), eventType, payload);
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private String normalizeVisibility(String visibility) {
        String normalized = visibility == null || visibility.isBlank() ? "inherited" : visibility.trim().toLowerCase();
        if (!normalized.equals("inherited") && !normalized.equals("private") && !normalized.equals("public")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility must be inherited, private, or public");
        }
        return normalized;
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean same(UUID left, UUID right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private BigDecimal toBigDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value.longValue());
    }

    private UUID firstNonNull(UUID first, UUID second) {
        return first == null ? second : first;
    }
}
