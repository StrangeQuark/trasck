package com.strangequark.trasck.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.team.ProjectTeamRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workflow.WorkflowStatus;
import com.strangequark.trasck.workflow.WorkflowStatusRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanningService {

    private final ObjectMapper objectMapper;
    private final IterationRepository iterationRepository;
    private final IterationWorkItemRepository iterationWorkItemRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public PlanningService(
            ObjectMapper objectMapper,
            IterationRepository iterationRepository,
            IterationWorkItemRepository iterationWorkItemRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            ProjectTeamRepository projectTeamRepository,
            WorkItemRepository workItemRepository,
            WorkflowStatusRepository workflowStatusRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.iterationRepository = iterationRepository;
        this.iterationWorkItemRepository = iterationWorkItemRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.workItemRepository = workItemRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<IterationResponse> listIterations(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.read");
        return iterationRepository.findByProjectIdOrderByStartDateAscNameAsc(project.getId()).stream()
                .map(IterationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IterationResponse getIteration(UUID iterationId) {
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = iteration(iterationId);
        permissionService.requireProjectPermission(actorId, requiredProjectId(iteration), "project.read");
        return IterationResponse.from(iteration);
    }

    @Transactional
    public IterationResponse createIteration(UUID projectId, IterationRequest request) {
        IterationRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        validateDates(createRequest);
        UUID teamId = validateTeam(project, createRequest.teamId());
        Iteration iteration = new Iteration();
        iteration.setWorkspaceId(project.getWorkspaceId());
        iteration.setProjectId(project.getId());
        iteration.setTeamId(teamId);
        iteration.setName(requiredText(createRequest.name(), "name"));
        iteration.setStartDate(createRequest.startDate());
        iteration.setEndDate(createRequest.endDate());
        iteration.setStatus(normalizeStatus(createRequest.status(), "planned"));
        iteration.setCommittedPoints(nonNegative(createRequest.committedPoints(), "committedPoints"));
        iteration.setCompletedPoints(nonNegative(createRequest.completedPoints(), "completedPoints"));
        Iteration saved = iterationRepository.save(iteration);
        recordIterationEvent(saved, "iteration.created", actorId);
        return IterationResponse.from(saved);
    }

    @Transactional
    public IterationResponse updateIteration(UUID iterationId, IterationRequest request) {
        IterationRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = iteration(iterationId);
        Project project = activeProject(requiredProjectId(iteration));
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        validateDates(firstNonNull(updateRequest.startDate(), iteration.getStartDate()), firstNonNull(updateRequest.endDate(), iteration.getEndDate()));
        if (hasText(updateRequest.name())) {
            iteration.setName(updateRequest.name().trim());
        }
        if (updateRequest.teamId() != null) {
            iteration.setTeamId(validateTeam(project, updateRequest.teamId()));
        }
        if (updateRequest.startDate() != null) {
            iteration.setStartDate(updateRequest.startDate());
        }
        if (updateRequest.endDate() != null) {
            iteration.setEndDate(updateRequest.endDate());
        }
        if (updateRequest.status() != null) {
            iteration.setStatus(normalizeStatus(updateRequest.status(), iteration.getStatus()));
        }
        if (updateRequest.committedPoints() != null) {
            iteration.setCommittedPoints(nonNegative(updateRequest.committedPoints(), "committedPoints"));
        }
        if (updateRequest.completedPoints() != null) {
            iteration.setCompletedPoints(nonNegative(updateRequest.completedPoints(), "completedPoints"));
        }
        Iteration saved = iterationRepository.save(iteration);
        recordIterationEvent(saved, "iteration.updated", actorId);
        return IterationResponse.from(saved);
    }

    @Transactional
    public void cancelIteration(UUID iterationId) {
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = iteration(iterationId);
        permissionService.requireProjectPermission(actorId, requiredProjectId(iteration), "board.admin");
        if (!"cancelled".equals(iteration.getStatus())) {
            iteration.setStatus("cancelled");
            Iteration saved = iterationRepository.save(iteration);
            recordIterationEvent(saved, "iteration.cancelled", actorId);
        }
    }

    @Transactional(readOnly = true)
    public List<IterationWorkItemResponse> listIterationWorkItems(UUID iterationId) {
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = iteration(iterationId);
        permissionService.requireProjectPermission(actorId, requiredProjectId(iteration), "project.read");
        return iterationWorkItemRepository.findByIdIterationIdOrderByAddedAtAsc(iteration.getId()).stream()
                .map(IterationWorkItemResponse::from)
                .toList();
    }

    @Transactional
    public IterationWorkItemResponse addWorkItem(UUID iterationId, IterationWorkItemRequest request) {
        IterationWorkItemRequest addRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = editableIteration(iterationId);
        Project project = activeProject(requiredProjectId(iteration));
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        WorkItem item = activeWorkItem(required(addRequest.workItemId(), "workItemId"));
        validateWorkItemForIteration(project, iteration, item);

        IterationWorkItem iterationWorkItem = iterationWorkItemRepository
                .findByIdIterationIdAndIdWorkItemId(iteration.getId(), item.getId())
                .orElseGet(IterationWorkItem::new);
        iterationWorkItem.setId(new IterationWorkItemId(iteration.getId(), item.getId()));
        iterationWorkItem.setAddedById(actorId);
        iterationWorkItem.setAddedAt(OffsetDateTime.now());
        iterationWorkItem.setRemovedAt(null);
        IterationWorkItem saved = iterationWorkItemRepository.save(iterationWorkItem);
        recordIterationWorkItemEvent(iteration, item, "iteration.work_item_added", actorId);
        return IterationWorkItemResponse.from(saved);
    }

    @Transactional
    public void removeWorkItem(UUID iterationId, UUID workItemId) {
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = editableIteration(iterationId);
        permissionService.requireProjectPermission(actorId, requiredProjectId(iteration), "board.admin");
        IterationWorkItem iterationWorkItem = iterationWorkItemRepository
                .findByIdIterationIdAndIdWorkItemId(iteration.getId(), workItemId)
                .orElseThrow(() -> notFound("Iteration work item not found"));
        if (iterationWorkItem.getRemovedAt() == null) {
            iterationWorkItem.setRemovedAt(OffsetDateTime.now());
            iterationWorkItemRepository.save(iterationWorkItem);
            workItemRepository.findByIdAndDeletedAtIsNull(workItemId)
                    .ifPresent(item -> recordIterationWorkItemEvent(iteration, item, "iteration.work_item_removed", actorId));
        }
    }

    @Transactional
    public IterationResponse commitIteration(UUID iterationId, IterationCommitRequest request) {
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = editableIteration(iterationId);
        permissionService.requireProjectPermission(actorId, requiredProjectId(iteration), "board.admin");
        BigDecimal committedPoints = request != null && request.committedPoints() != null
                ? nonNegative(request.committedPoints(), "committedPoints")
                : sumEstimatePoints(iteration, false);
        iteration.setCommittedPoints(committedPoints);
        if (request == null || !Boolean.FALSE.equals(request.activate())) {
            iteration.setStatus("active");
        }
        Iteration saved = iterationRepository.save(iteration);
        recordIterationEvent(saved, "iteration.committed", actorId);
        return IterationResponse.from(saved);
    }

    @Transactional
    public IterationResponse closeIteration(UUID iterationId, IterationCloseRequest request) {
        UUID actorId = currentUserService.requireUserId();
        Iteration iteration = iteration(iterationId);
        if (List.of("closed", "cancelled").contains(iteration.getStatus())) {
            throw badRequest("Closed or cancelled iterations cannot be closed");
        }
        permissionService.requireProjectPermission(actorId, requiredProjectId(iteration), "board.admin");
        BigDecimal completedPoints = request != null && request.completedPoints() != null
                ? nonNegative(request.completedPoints(), "completedPoints")
                : sumEstimatePoints(iteration, true);
        int carriedOver = 0;
        if (request != null && Boolean.TRUE.equals(request.carryOverIncomplete())) {
            Iteration target = editableIteration(required(request.carryOverIterationId(), "carryOverIterationId"));
            validateCarryOverTarget(iteration, target);
            carriedOver = carryOverIncomplete(iteration, target, actorId);
        }
        iteration.setCompletedPoints(completedPoints);
        iteration.setStatus("closed");
        Iteration saved = iterationRepository.save(iteration);
        ObjectNode payload = iterationPayload(saved, actorId).put("carriedOverWorkItems", carriedOver);
        domainEventService.record(saved.getWorkspaceId(), "iteration", saved.getId(), "iteration.closed", payload);
        return IterationResponse.from(saved);
    }

    private int carryOverIncomplete(Iteration source, Iteration target, UUID actorId) {
        int count = 0;
        for (IterationWorkItem scopedItem : iterationWorkItemRepository.findByIdIterationIdAndRemovedAtIsNullOrderByAddedAtAsc(source.getId())) {
            WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(scopedItem.getId().getWorkItemId()).orElse(null);
            if (item == null || isTerminal(item)) {
                continue;
            }
            IterationWorkItem targetItem = iterationWorkItemRepository
                    .findByIdIterationIdAndIdWorkItemId(target.getId(), item.getId())
                    .orElseGet(IterationWorkItem::new);
            if (targetItem.getId().getIterationId() != null && targetItem.getRemovedAt() == null) {
                continue;
            }
            targetItem.setId(new IterationWorkItemId(target.getId(), item.getId()));
            targetItem.setAddedById(actorId);
            targetItem.setAddedAt(OffsetDateTime.now());
            targetItem.setRemovedAt(null);
            iterationWorkItemRepository.save(targetItem);
            count++;
        }
        if (count > 0) {
            ObjectNode payload = iterationPayload(target, actorId).put("carriedOverWorkItems", count).put("sourceIterationId", source.getId().toString());
            domainEventService.record(target.getWorkspaceId(), "iteration", target.getId(), "iteration.work_items_carried_over", payload);
        }
        return count;
    }

    private BigDecimal sumEstimatePoints(Iteration iteration, boolean terminalOnly) {
        BigDecimal total = BigDecimal.ZERO;
        for (IterationWorkItem scopedItem : iterationWorkItemRepository.findByIdIterationIdAndRemovedAtIsNullOrderByAddedAtAsc(iteration.getId())) {
            WorkItem item = workItemRepository.findByIdAndDeletedAtIsNull(scopedItem.getId().getWorkItemId()).orElse(null);
            if (item == null || terminalOnly && !isTerminal(item) || item.getEstimatePoints() == null) {
                continue;
            }
            total = total.add(item.getEstimatePoints());
        }
        return total;
    }

    private boolean isTerminal(WorkItem item) {
        return workflowStatusRepository.findById(item.getStatusId())
                .map(WorkflowStatus::getTerminal)
                .orElse(false);
    }

    private void validateWorkItemForIteration(Project project, Iteration iteration, WorkItem item) {
        if (!project.getWorkspaceId().equals(item.getWorkspaceId()) || !project.getId().equals(item.getProjectId())) {
            throw badRequest("Work item must belong to the iteration project");
        }
        if (iteration.getTeamId() != null && item.getTeamId() != null && !iteration.getTeamId().equals(item.getTeamId())) {
            throw badRequest("Work item team does not match the iteration team");
        }
    }

    private void validateCarryOverTarget(Iteration source, Iteration target) {
        if (source.getId().equals(target.getId())) {
            throw badRequest("Carryover iteration must be different from the closed iteration");
        }
        if (!source.getWorkspaceId().equals(target.getWorkspaceId())
                || !same(source.getProjectId(), target.getProjectId())
                || !same(source.getTeamId(), target.getTeamId())) {
            throw badRequest("Carryover iteration must use the same workspace, project, and team");
        }
    }

    private UUID validateTeam(Project project, UUID teamId) {
        if (teamId == null) {
            return null;
        }
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, project.getWorkspaceId())
                .orElseThrow(() -> badRequest("Team not found in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw badRequest("Team is not active");
        }
        if (!projectTeamRepository.existsByIdProjectIdAndIdTeamId(project.getId(), team.getId())) {
            throw badRequest("Team is not assigned to this project");
        }
        return team.getId();
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

    private Iteration iteration(UUID iterationId) {
        return iterationRepository.findById(iterationId).orElseThrow(() -> notFound("Iteration not found"));
    }

    private Iteration editableIteration(UUID iterationId) {
        Iteration iteration = iteration(iterationId);
        if (List.of("closed", "cancelled").contains(iteration.getStatus())) {
            throw badRequest("Closed or cancelled iterations cannot be changed");
        }
        return iteration;
    }

    private UUID requiredProjectId(Iteration iteration) {
        if (iteration.getProjectId() == null) {
            throw badRequest("Only project-scoped iterations are supported by these APIs");
        }
        return iteration.getProjectId();
    }

    private void validateDates(IterationRequest request) {
        validateDates(request.startDate(), request.endDate());
    }

    private void validateDates(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw badRequest("startDate must be on or before endDate");
        }
    }

    private void recordIterationEvent(Iteration iteration, String eventType, UUID actorId) {
        domainEventService.record(iteration.getWorkspaceId(), "iteration", iteration.getId(), eventType, iterationPayload(iteration, actorId));
    }

    private void recordIterationWorkItemEvent(Iteration iteration, WorkItem item, String eventType, UUID actorId) {
        ObjectNode payload = iterationPayload(iteration, actorId)
                .put("workItemId", item.getId().toString())
                .put("workItemKey", item.getKey());
        domainEventService.record(iteration.getWorkspaceId(), "iteration", iteration.getId(), eventType, payload);
    }

    private ObjectNode iterationPayload(Iteration iteration, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("iterationId", iteration.getId().toString())
                .put("iterationName", iteration.getName())
                .put("status", iteration.getStatus())
                .put("actorUserId", actorId.toString());
        if (iteration.getProjectId() != null) {
            payload.put("projectId", iteration.getProjectId().toString());
        }
        if (iteration.getTeamId() != null) {
            payload.put("teamId", iteration.getTeamId().toString());
        }
        return payload;
    }

    private String normalizeStatus(String status, String defaultStatus) {
        String normalized = hasText(status) ? status.trim().toLowerCase() : defaultStatus;
        if (!List.of("planned", "active", "closed", "cancelled").contains(normalized)) {
            throw badRequest("status must be planned, active, closed, or cancelled");
        }
        return normalized;
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw badRequest(fieldName + " must be zero or greater");
        }
        return value;
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
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

    private boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
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
