package com.strangequark.trasck.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.search.SavedFilter;
import com.strangequark.trasck.search.SavedFilterExecutionService;
import com.strangequark.trasck.search.SavedFilterService;
import com.strangequark.trasck.team.ProjectTeamRepository;
import com.strangequark.trasck.team.Team;
import com.strangequark.trasck.team.TeamRepository;
import com.strangequark.trasck.workitem.WorkItem;
import com.strangequark.trasck.workitem.WorkItemRepository;
import com.strangequark.trasck.workitem.WorkItemRankRequest;
import com.strangequark.trasck.workitem.WorkItemResponse;
import com.strangequark.trasck.workitem.WorkItemService;
import com.strangequark.trasck.workitem.WorkItemTransitionRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BoardService {

    private final ObjectMapper objectMapper;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardSwimlaneRepository boardSwimlaneRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemService workItemService;
    private final SavedFilterExecutionService savedFilterExecutionService;
    private final SavedFilterService savedFilterService;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public BoardService(
            ObjectMapper objectMapper,
            BoardRepository boardRepository,
            BoardColumnRepository boardColumnRepository,
            BoardSwimlaneRepository boardSwimlaneRepository,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            ProjectTeamRepository projectTeamRepository,
            WorkItemRepository workItemRepository,
            WorkItemService workItemService,
            SavedFilterExecutionService savedFilterExecutionService,
            SavedFilterService savedFilterService,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.boardRepository = boardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.boardSwimlaneRepository = boardSwimlaneRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.workItemRepository = workItemRepository;
        this.workItemService = workItemService;
        this.savedFilterExecutionService = savedFilterExecutionService;
        this.savedFilterService = savedFilterService;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<BoardResponse> listProjectBoards(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.read");
        return boardRepository.findByProjectIdAndActiveTrueOrderByNameAsc(project.getId()).stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(UUID boardId) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "project.read");
        return response(board);
    }

    @Transactional
    public BoardResponse createBoard(UUID projectId, BoardRequest request) {
        BoardRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        Board board = new Board();
        board.setWorkspaceId(project.getWorkspaceId());
        board.setProjectId(project.getId());
        applyBoardRequest(project, board, createRequest, true);
        OffsetDateTime now = OffsetDateTime.now();
        board.setCreatedAt(now);
        board.setUpdatedAt(now);
        Board saved = boardRepository.save(board);
        recordBoardEvent(saved, "board.created", actorId);
        return response(saved);
    }

    @Transactional
    public BoardResponse updateBoard(UUID boardId, BoardRequest request) {
        BoardRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        Project project = activeProject(board.getProjectId());
        permissionService.requireProjectPermission(actorId, project.getId(), "board.admin");
        applyBoardRequest(project, board, updateRequest, false);
        board.setUpdatedAt(OffsetDateTime.now());
        board.setVersion(board.getVersion() == null ? 1 : board.getVersion() + 1);
        Board saved = boardRepository.save(board);
        recordBoardEvent(saved, "board.updated", actorId);
        return response(saved);
    }

    @Transactional
    public void archiveBoard(UUID boardId) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        board.setActive(false);
        board.setUpdatedAt(OffsetDateTime.now());
        board.setVersion(board.getVersion() == null ? 1 : board.getVersion() + 1);
        boardRepository.save(board);
        recordBoardEvent(board, "board.archived", actorId);
    }

    @Transactional(readOnly = true)
    public List<BoardColumnResponse> listColumns(UUID boardId) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "project.read");
        return boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId()).stream()
                .map(BoardColumnResponse::from)
                .toList();
    }

    @Transactional
    public BoardColumnResponse createColumn(UUID boardId, BoardColumnRequest request) {
        BoardColumnRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        BoardColumn column = new BoardColumn();
        column.setBoardId(board.getId());
        applyColumnRequest(column, createRequest, true);
        BoardColumn saved = boardColumnRepository.save(column);
        recordBoardEvent(board, "board.column_created", actorId);
        return BoardColumnResponse.from(saved);
    }

    @Transactional
    public BoardColumnResponse updateColumn(UUID boardId, UUID columnId, BoardColumnRequest request) {
        BoardColumnRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        BoardColumn column = boardColumnRepository.findByIdAndBoardId(columnId, board.getId())
                .orElseThrow(() -> notFound("Board column not found"));
        applyColumnRequest(column, updateRequest, false);
        BoardColumn saved = boardColumnRepository.save(column);
        recordBoardEvent(board, "board.column_updated", actorId);
        return BoardColumnResponse.from(saved);
    }

    @Transactional
    public void deleteColumn(UUID boardId, UUID columnId) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        BoardColumn column = boardColumnRepository.findByIdAndBoardId(columnId, board.getId())
                .orElseThrow(() -> notFound("Board column not found"));
        boardColumnRepository.delete(column);
        recordBoardEvent(board, "board.column_deleted", actorId);
    }

    @Transactional(readOnly = true)
    public List<BoardSwimlaneResponse> listSwimlanes(UUID boardId) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "project.read");
        return boardSwimlaneRepository.findByBoardIdOrderByPositionAsc(board.getId()).stream()
                .map(BoardSwimlaneResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardWorkItemsResponse listBoardWorkItems(UUID boardId, Integer limitPerColumn) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "work_item.read");
        int limit = normalizeLimitPerColumn(limitPerColumn);
        List<BoardColumnWorkItemsResponse> columns = boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId()).stream()
                .map(column -> {
                    List<UUID> statusIds = statusIds(column.getStatusIds());
                    List<WorkItemResponse> items = statusIds.isEmpty()
                            ? List.of()
                            : workItemRepository.findByProjectIdAndStatusIdInAndDeletedAtIsNullOrderByRankAsc(board.getProjectId(), statusIds)
                                    .stream()
                                    .limit(limit)
                                    .map(WorkItemResponse::from)
                                    .toList();
                    return new BoardColumnWorkItemsResponse(column.getId(), column.getName(), statusIds, items);
                })
                .toList();
        List<BoardSwimlaneWorkItemsResponse> swimlanes = boardSwimlaneRepository.findByBoardIdOrderByPositionAsc(board.getId()).stream()
                .filter(swimlane -> Boolean.TRUE.equals(swimlane.getEnabled()))
                .map(swimlane -> new BoardSwimlaneWorkItemsResponse(
                        swimlane.getId(),
                        swimlane.getName(),
                        swimlane.getSwimlaneType(),
                        swimlaneColumns(board, swimlane, columns, limit)
                ))
                .toList();
        return new BoardWorkItemsResponse(board.getId(), board.getProjectId(), limit, columns, swimlanes);
    }

    @Transactional
    public WorkItemResponse rankBoardWorkItem(UUID boardId, UUID workItemId, BoardWorkItemRankRequest request) {
        BoardWorkItemRankRequest rankRequest = required(request, "request");
        Board board = activeBoard(boardId);
        requireWorkItemOnBoard(board, workItemId);
        if (rankRequest.previousWorkItemId() != null) {
            requireWorkItemOnBoard(board, rankRequest.previousWorkItemId());
        }
        if (rankRequest.nextWorkItemId() != null) {
            requireWorkItemOnBoard(board, rankRequest.nextWorkItemId());
        }
        return workItemService.rank(
                workItemId,
                new WorkItemRankRequest(rankRequest.previousWorkItemId(), rankRequest.nextWorkItemId())
        );
    }

    @Transactional
    public WorkItemResponse transitionBoardWorkItem(UUID boardId, UUID workItemId, BoardWorkItemTransitionRequest request) {
        BoardWorkItemTransitionRequest transitionRequest = required(request, "request");
        Board board = activeBoard(boardId);
        WorkItem item = requireWorkItemOnBoard(board, workItemId);
        if (hasText(transitionRequest.transitionKey())) {
            return workItemService.transition(workItemId, new WorkItemTransitionRequest(transitionRequest.transitionKey()));
        }
        UUID targetStatusId = targetBoardStatusId(board, item, transitionRequest);
        return workItemService.transitionToStatus(workItemId, targetStatusId);
    }

    @Transactional
    public WorkItemResponse moveBoardWorkItem(UUID boardId, UUID workItemId, BoardWorkItemMoveRequest request) {
        BoardWorkItemMoveRequest moveRequest = required(request, "request");
        Board board = activeBoard(boardId);
        WorkItem item = requireWorkItemOnBoard(board, workItemId);
        boolean hasRankChange = moveRequest.previousWorkItemId() != null || moveRequest.nextWorkItemId() != null;
        boolean hasTransitionChange = hasText(moveRequest.transitionKey())
                || moveRequest.targetColumnId() != null
                || moveRequest.targetStatusId() != null;
        if (!hasRankChange && !hasTransitionChange) {
            throw badRequest("targetColumnId, targetStatusId, transitionKey, previousWorkItemId, or nextWorkItemId is required");
        }
        WorkItem previous = moveRequest.previousWorkItemId() == null ? null : requireWorkItemOnBoard(board, moveRequest.previousWorkItemId());
        WorkItem next = moveRequest.nextWorkItemId() == null ? null : requireWorkItemOnBoard(board, moveRequest.nextWorkItemId());
        validateRelativeItemsForTargetColumn(board, moveRequest.targetColumnId(), previous, next);
        WorkItemResponse response = WorkItemResponse.from(item);
        if (hasTransitionChange) {
            if (hasText(moveRequest.transitionKey())) {
                response = workItemService.transition(workItemId, new WorkItemTransitionRequest(moveRequest.transitionKey()));
            } else {
                UUID targetStatusId = targetBoardStatusId(
                        board,
                        item,
                        new BoardWorkItemTransitionRequest(null, moveRequest.targetColumnId(), moveRequest.targetStatusId())
                );
                if (!targetStatusId.equals(item.getStatusId())) {
                    response = workItemService.transitionToStatus(workItemId, targetStatusId);
                }
            }
        }
        if (hasRankChange) {
            response = workItemService.rank(
                    workItemId,
                    new WorkItemRankRequest(moveRequest.previousWorkItemId(), moveRequest.nextWorkItemId())
            );
        }
        return response;
    }

    @Transactional
    public BoardSwimlaneResponse createSwimlane(UUID boardId, BoardSwimlaneRequest request) {
        BoardSwimlaneRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        BoardSwimlane swimlane = new BoardSwimlane();
        swimlane.setBoardId(board.getId());
        applySwimlaneRequest(board, actorId, swimlane, createRequest, true);
        BoardSwimlane saved = boardSwimlaneRepository.save(swimlane);
        recordBoardEvent(board, "board.swimlane_created", actorId);
        return BoardSwimlaneResponse.from(saved);
    }

    @Transactional
    public BoardSwimlaneResponse updateSwimlane(UUID boardId, UUID swimlaneId, BoardSwimlaneRequest request) {
        BoardSwimlaneRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        BoardSwimlane swimlane = boardSwimlaneRepository.findByIdAndBoardId(swimlaneId, board.getId())
                .orElseThrow(() -> notFound("Board swimlane not found"));
        applySwimlaneRequest(board, actorId, swimlane, updateRequest, false);
        BoardSwimlane saved = boardSwimlaneRepository.save(swimlane);
        recordBoardEvent(board, "board.swimlane_updated", actorId);
        return BoardSwimlaneResponse.from(saved);
    }

    @Transactional
    public void deleteSwimlane(UUID boardId, UUID swimlaneId) {
        UUID actorId = currentUserService.requireUserId();
        Board board = activeBoard(boardId);
        permissionService.requireProjectPermission(actorId, board.getProjectId(), "board.admin");
        BoardSwimlane swimlane = boardSwimlaneRepository.findByIdAndBoardId(swimlaneId, board.getId())
                .orElseThrow(() -> notFound("Board swimlane not found"));
        boardSwimlaneRepository.delete(swimlane);
        recordBoardEvent(board, "board.swimlane_deleted", actorId);
    }

    private void applyBoardRequest(Project project, Board board, BoardRequest request, boolean create) {
        if (create || hasText(request.name())) {
            board.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.type())) {
            board.setType(normalizeBoardType(requiredText(request.type(), "type")));
        }
        if (create || request.teamId() != null) {
            board.setTeamId(request.teamId() == null ? null : validateTeam(project, request.teamId()).getId());
        }
        if (create || request.filterConfig() != null) {
            board.setFilterConfig(toJsonObject(request.filterConfig()));
        }
        if (create) {
            board.setActive(!Boolean.FALSE.equals(request.active()));
        } else if (request.active() != null) {
            board.setActive(request.active());
        }
    }

    private void applyColumnRequest(BoardColumn column, BoardColumnRequest request, boolean create) {
        if (create || hasText(request.name())) {
            column.setName(requiredText(request.name(), "name"));
        }
        if (create || request.statusIds() != null) {
            column.setStatusIds(toJsonArray(request.statusIds(), "statusIds"));
        }
        if (request.position() != null) {
            column.setPosition(nonNegative(request.position(), "position"));
        } else if (create) {
            column.setPosition(0);
        }
        if (request.wipLimit() != null) {
            column.setWipLimit(nonNegative(request.wipLimit(), "wipLimit"));
        }
        if (request.doneColumn() != null) {
            column.setDoneColumn(request.doneColumn());
        } else if (create) {
            column.setDoneColumn(false);
        }
    }

    private void applySwimlaneRequest(
            Board board,
            UUID actorId,
            BoardSwimlane swimlane,
            BoardSwimlaneRequest request,
            boolean create
    ) {
        if (create || hasText(request.name())) {
            swimlane.setName(requiredText(request.name(), "name"));
        }
        if (create || hasText(request.swimlaneType())) {
            swimlane.setSwimlaneType(requiredText(request.swimlaneType(), "swimlaneType").toLowerCase());
        }
        if (Boolean.TRUE.equals(request.clearSavedFilter())) {
            swimlane.setSavedFilterId(null);
        } else if (request.savedFilterId() != null) {
            SavedFilter savedFilter = savedFilterService.requireReadableEntity(request.savedFilterId(), actorId);
            if (!board.getWorkspaceId().equals(savedFilter.getWorkspaceId())) {
                throw badRequest("savedFilterId is not in this board workspace");
            }
            swimlane.setSavedFilterId(savedFilter.getId());
        }
        if (create || request.query() != null) {
            swimlane.setQuery(toJsonObject(request.query()));
        }
        if (request.position() != null) {
            swimlane.setPosition(nonNegative(request.position(), "position"));
        } else if (create) {
            swimlane.setPosition(0);
        }
        if (request.enabled() != null) {
            swimlane.setEnabled(request.enabled());
        } else if (create) {
            swimlane.setEnabled(true);
        }
    }

    private BoardResponse response(Board board) {
        return BoardResponse.from(
                board,
                boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId()),
                boardSwimlaneRepository.findByBoardIdOrderByPositionAsc(board.getId())
        );
    }

    private Board activeBoard(UUID boardId) {
        Board board = boardRepository.findById(boardId).orElseThrow(() -> notFound("Board not found"));
        if (!Boolean.TRUE.equals(board.getActive()) || board.getProjectId() == null) {
            throw notFound("Board not found");
        }
        return board;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> notFound("Project not found"));
        if (!"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        return project;
    }

    private Team validateTeam(Project project, UUID teamId) {
        Team team = teamRepository.findByIdAndWorkspaceId(teamId, project.getWorkspaceId())
                .orElseThrow(() -> badRequest("Team not found in this workspace"));
        if (!"active".equals(team.getStatus())) {
            throw badRequest("Team is not active");
        }
        if (!projectTeamRepository.existsByIdProjectIdAndIdTeamId(project.getId(), team.getId())) {
            throw badRequest("Team is not assigned to this project");
        }
        return team;
    }

    private String normalizeBoardType(String type) {
        String normalized = type.toLowerCase();
        if (!List.of("scrum", "kanban", "portfolio").contains(normalized)) {
            throw badRequest("type must be scrum, kanban, or portfolio");
        }
        return normalized;
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

    private JsonNode toJsonArray(Object value, String fieldName) {
        JsonNode json = toJsonNullable(value);
        if (json == null || json.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (!json.isArray()) {
            throw badRequest(fieldName + " must be a JSON array");
        }
        return json;
    }

    private List<UUID> statusIds(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && item.isTextual() && hasText(item.asText())) {
                try {
                    ids.add(UUID.fromString(item.asText()));
                } catch (IllegalArgumentException ignored) {
                    // Invalid status IDs are ignored in board rendering so one bad config does not hide the whole board.
                }
            }
        }
        return ids;
    }

    private UUID targetBoardStatusId(Board board, WorkItem item, BoardWorkItemTransitionRequest request) {
        BoardColumn targetColumn = request.targetColumnId() == null
                ? null
                : boardColumnRepository.findByIdAndBoardId(request.targetColumnId(), board.getId())
                        .orElseThrow(() -> badRequest("targetColumnId is not on this board"));
        UUID requestedStatusId = request.targetStatusId();
        if (targetColumn == null && requestedStatusId == null) {
            throw badRequest("transitionKey, targetColumnId, or targetStatusId is required");
        }
        if (targetColumn != null) {
            List<UUID> columnStatusIds = statusIds(targetColumn.getStatusIds());
            if (columnStatusIds.isEmpty()) {
                throw badRequest("Target board column has no mapped statuses");
            }
            if (requestedStatusId != null && !columnStatusIds.contains(requestedStatusId)) {
                throw badRequest("targetStatusId is not mapped to the target column");
            }
            return requestedStatusId == null
                    ? columnStatusIds.stream()
                            .filter(statusId -> !statusId.equals(item.getStatusId()))
                            .findFirst()
                            .orElse(columnStatusIds.get(0))
                    : requestedStatusId;
        }
        boolean statusMappedToBoard = boardColumnRepository.findByBoardIdOrderByPositionAsc(board.getId()).stream()
                .map(BoardColumn::getStatusIds)
                .map(this::statusIds)
                .anyMatch(statusIds -> statusIds.contains(requestedStatusId));
        if (!statusMappedToBoard) {
            throw badRequest("targetStatusId is not mapped to this board");
        }
        return requestedStatusId;
    }

    private void validateRelativeItemsForTargetColumn(Board board, UUID targetColumnId, WorkItem previous, WorkItem next) {
        if (targetColumnId == null || previous == null && next == null) {
            return;
        }
        BoardColumn targetColumn = boardColumnRepository.findByIdAndBoardId(targetColumnId, board.getId())
                .orElseThrow(() -> badRequest("targetColumnId is not on this board"));
        List<UUID> targetStatuses = statusIds(targetColumn.getStatusIds());
        if (targetStatuses.isEmpty()) {
            throw badRequest("Target board column has no mapped statuses");
        }
        if (previous != null && !targetStatuses.contains(previous.getStatusId())) {
            throw badRequest("previousWorkItemId is not in the target column");
        }
        if (next != null && !targetStatuses.contains(next.getStatusId())) {
            throw badRequest("nextWorkItemId is not in the target column");
        }
    }

    private List<BoardColumnWorkItemsResponse> swimlaneColumns(
            Board board,
            BoardSwimlane swimlane,
            List<BoardColumnWorkItemsResponse> columns,
            int limitPerColumn
    ) {
        int queryLimit = swimlaneQueryLimit(columns.size(), limitPerColumn);
        List<UUID> matchingWorkItemIds = swimlane.getSavedFilterId() == null
                ? savedFilterExecutionService.executeInlineWorkItemIds(
                        board.getWorkspaceId(),
                        swimlaneQuery(board, swimlane),
                        queryLimit
                )
                : savedFilterExecutionService.executeSavedFilterWorkItemIds(
                        swimlane.getSavedFilterId(),
                        board.getProjectId(),
                        queryLimit
                );
        Set<UUID> matchingIds = new LinkedHashSet<>(matchingWorkItemIds);
        return columns.stream()
                .map(column -> new BoardColumnWorkItemsResponse(
                        column.columnId(),
                        column.columnName(),
                        column.statusIds(),
                        column.workItems().stream()
                                .filter(item -> matchingIds.contains(item.id()))
                                .toList()
                ))
                .toList();
    }

    private ObjectNode swimlaneQuery(Board board, BoardSwimlane swimlane) {
        JsonNode query = swimlane.getQuery();
        String swimlaneType = hasText(swimlane.getSwimlaneType()) ? swimlane.getSwimlaneType().trim().toLowerCase() : "query";
        ObjectNode executable = "query".equals(swimlaneType)
                ? querySwimlaneFilter(query)
                : simpleSwimlaneFilter(swimlaneType, query);
        executable.put("entityType", "work_item");
        executable.put("projectId", board.getProjectId().toString());
        executable.remove("projectIds");
        if (!executable.has("sort")) {
            executable.set("sort", objectMapper.createObjectNode()
                    .put("field", "rank")
                    .put("direction", "asc"));
        }
        return executable;
    }

    private ObjectNode querySwimlaneFilter(JsonNode query) {
        ObjectNode executable = query != null && query.isObject()
                ? query.deepCopy()
                : objectMapper.createObjectNode();
        if (!executable.has("where") && !executable.has("filters") && hasText(text(executable, "field"))) {
            ObjectNode predicate = executable.deepCopy();
            executable.remove(List.of("field", "operator", "value", "valueTo", "values", "customFieldKey", "customFieldId"));
            executable.set("where", predicate);
        }
        return executable;
    }

    private ObjectNode simpleSwimlaneFilter(String swimlaneType, JsonNode query) {
        ObjectNode executable = objectMapper.createObjectNode();
        String field = switch (swimlaneType) {
            case "team" -> "teamId";
            case "assignee" -> "assigneeId";
            case "reporter" -> "reporterId";
            case "type" -> "typeId";
            case "priority" -> "priorityId";
            default -> "id";
        };
        ObjectNode predicate = objectMapper.createObjectNode().put("field", field);
        String expected = firstText(text(query, "value"), text(query, "id"), text(query, "uuid"));
        if (!hasText(expected)) {
            predicate.put("operator", "is_not_null");
        } else if ("unassigned".equalsIgnoreCase(expected) || "none".equalsIgnoreCase(expected) || "null".equalsIgnoreCase(expected)) {
            predicate.put("operator", "is_null");
        } else {
            predicate.put("operator", "eq");
            predicate.put("value", expected.trim());
        }
        executable.set("where", predicate);
        return executable;
    }

    private int swimlaneQueryLimit(int columnCount, int limitPerColumn) {
        return Math.min(1000, Math.max(limitPerColumn, Math.max(1, columnCount) * limitPerColumn * 4));
    }

    private String text(JsonNode query, String fieldName) {
        return query != null && query.hasNonNull(fieldName) ? query.get(fieldName).asText() : null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private WorkItem requireWorkItemOnBoard(Board board, UUID workItemId) {
        return workItemRepository.findActiveInProject(workItemId, board.getProjectId())
                .orElseThrow(() -> notFound("Work item not found on this board"));
    }

    private void recordBoardEvent(Board board, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("boardId", board.getId().toString())
                .put("boardName", board.getName())
                .put("projectId", board.getProjectId().toString())
                .put("actorUserId", actorId.toString());
        if (board.getTeamId() != null) {
            payload.put("teamId", board.getTeamId().toString());
        }
        domainEventService.record(board.getWorkspaceId(), "board", board.getId(), eventType, payload);
    }

    private int nonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw badRequest(fieldName + " must be zero or greater");
        }
        return value;
    }

    private int normalizeLimitPerColumn(Integer limitPerColumn) {
        if (limitPerColumn == null) {
            return 50;
        }
        if (limitPerColumn < 1 || limitPerColumn > 200) {
            throw badRequest("limitPerColumn must be between 1 and 200");
        }
        return limitPerColumn;
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
