package com.strangequark.trasck.setup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.Permission;
import com.strangequark.trasck.access.PermissionRepository;
import com.strangequark.trasck.access.ProjectMembership;
import com.strangequark.trasck.access.ProjectMembershipRepository;
import com.strangequark.trasck.access.Role;
import com.strangequark.trasck.access.RolePermission;
import com.strangequark.trasck.access.RolePermissionId;
import com.strangequark.trasck.access.RolePermissionRepository;
import com.strangequark.trasck.access.RoleRepository;
import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.strangequark.trasck.activity.AttachmentStorageConfigRepository;
import com.strangequark.trasck.board.Board;
import com.strangequark.trasck.board.BoardColumn;
import com.strangequark.trasck.board.BoardColumnRepository;
import com.strangequark.trasck.board.BoardRepository;
import com.strangequark.trasck.board.BoardSwimlane;
import com.strangequark.trasck.board.BoardSwimlaneRepository;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectSettings;
import com.strangequark.trasck.project.ProjectSettingsRepository;
import com.strangequark.trasck.workflow.Workflow;
import com.strangequark.trasck.workflow.WorkflowAssignment;
import com.strangequark.trasck.workflow.WorkflowAssignmentRepository;
import com.strangequark.trasck.workflow.WorkflowRepository;
import com.strangequark.trasck.workflow.WorkflowStatus;
import com.strangequark.trasck.workflow.WorkflowStatusRepository;
import com.strangequark.trasck.workflow.WorkflowTransition;
import com.strangequark.trasck.workflow.WorkflowTransitionAction;
import com.strangequark.trasck.workflow.WorkflowTransitionActionRepository;
import com.strangequark.trasck.workflow.WorkflowTransitionRepository;
import com.strangequark.trasck.workflow.WorkflowTransitionRule;
import com.strangequark.trasck.workflow.WorkflowTransitionRuleRepository;
import com.strangequark.trasck.workitem.Priority;
import com.strangequark.trasck.workitem.PriorityRepository;
import com.strangequark.trasck.workitem.ProjectWorkItemType;
import com.strangequark.trasck.workitem.ProjectWorkItemTypeRepository;
import com.strangequark.trasck.workitem.Resolution;
import com.strangequark.trasck.workitem.ResolutionRepository;
import com.strangequark.trasck.workitem.WorkItemType;
import com.strangequark.trasck.workitem.WorkItemTypeRepository;
import com.strangequark.trasck.workitem.WorkItemTypeRule;
import com.strangequark.trasck.workitem.WorkItemTypeRuleRepository;
import com.strangequark.trasck.workspace.Workspace;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceSeedService {

    private static final List<TypeSeed> TYPE_SEEDS = List.of(
            new TypeSeed("Theme", "theme", 600, false, "layers", "#6B7280"),
            new TypeSeed("Initiative", "initiative", 500, false, "flag", "#2563EB"),
            new TypeSeed("Capability", "capability", 400, false, "package", "#0891B2"),
            new TypeSeed("Feature", "feature", 300, false, "box", "#059669"),
            new TypeSeed("Epic", "epic", 200, false, "bolt", "#7C3AED"),
            new TypeSeed("Story", "story", 100, false, "book-open", "#16A34A"),
            new TypeSeed("Task", "task", 100, false, "check-square", "#0EA5E9"),
            new TypeSeed("Bug", "bug", 100, false, "bug", "#DC2626"),
            new TypeSeed("Subtask", "subtask", 0, true, "list-checks", "#64748B")
    );

    private static final List<TypeRuleSeed> TYPE_RULE_SEEDS = List.of(
            new TypeRuleSeed("theme", "initiative"),
            new TypeRuleSeed("initiative", "capability"),
            new TypeRuleSeed("capability", "feature"),
            new TypeRuleSeed("feature", "epic"),
            new TypeRuleSeed("epic", "story"),
            new TypeRuleSeed("epic", "task"),
            new TypeRuleSeed("epic", "bug"),
            new TypeRuleSeed("story", "subtask"),
            new TypeRuleSeed("task", "subtask"),
            new TypeRuleSeed("bug", "subtask")
    );

    private static final List<PrioritySeed> PRIORITY_SEEDS = List.of(
            new PrioritySeed("Lowest", "lowest", "#94A3B8", 10, false),
            new PrioritySeed("Low", "low", "#38BDF8", 20, false),
            new PrioritySeed("Medium", "medium", "#22C55E", 30, true),
            new PrioritySeed("High", "high", "#F59E0B", 40, false),
            new PrioritySeed("Critical", "critical", "#DC2626", 50, false)
    );

    private static final List<ResolutionSeed> RESOLUTION_SEEDS = List.of(
            new ResolutionSeed("Done", "done", "done", 10, true),
            new ResolutionSeed("Duplicate", "duplicate", "closed", 20, false),
            new ResolutionSeed("Won't Do", "wont_do", "closed", 30, false),
            new ResolutionSeed("Cannot Reproduce", "cannot_reproduce", "closed", 40, false),
            new ResolutionSeed("Moved", "moved", "closed", 50, false)
    );

    private static final List<StatusSeed> STATUS_SEEDS = List.of(
            new StatusSeed("Open", "open", "todo", "#94A3B8", 10, false),
            new StatusSeed("Ready", "ready", "todo", "#0EA5E9", 20, false),
            new StatusSeed("In Progress", "in_progress", "in_progress", "#F59E0B", 30, false),
            new StatusSeed("In Review", "in_review", "in_progress", "#8B5CF6", 40, false),
            new StatusSeed("Approval", "approval", "in_progress", "#DB2777", 50, false),
            new StatusSeed("Blocked", "blocked", "in_progress", "#DC2626", 60, false),
            new StatusSeed("Done", "done", "done", "#16A34A", 70, true)
    );

    private static final List<TransitionSeed> TRANSITION_SEEDS = List.of(
            new TransitionSeed("open", "ready", "Mark Ready", "open_to_ready", 10),
            new TransitionSeed("ready", "in_progress", "Start Progress", "ready_to_in_progress", 20),
            new TransitionSeed("in_progress", "in_review", "Request Review", "in_progress_to_in_review", 30),
            new TransitionSeed("in_review", "approval", "Request Approval", "in_review_to_approval", 40),
            new TransitionSeed("approval", "done", "Approve And Complete", "approval_to_done", 50),
            new TransitionSeed("in_progress", "blocked", "Mark Blocked", "in_progress_to_blocked", 60),
            new TransitionSeed("blocked", "in_progress", "Resume Progress", "blocked_to_in_progress", 70),
            new TransitionSeed("in_review", "in_progress", "Request Changes", "in_review_to_in_progress", 80),
            new TransitionSeed("approval", "in_review", "Request Approval Changes", "approval_to_in_review", 90)
    );

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "workspace_owner", List.of("*"),
            "workspace_admin", List.of("workspace.admin", "workspace.read", "user.manage", "project.create", "project.admin", "project.read",
                    "work_item.create", "work_item.read", "work_item.update", "work_item.delete", "work_item.transition",
                    "work_item.comment", "work_item.link", "work_log.create_own", "work_log.update_own", "work_log.delete_own",
                    "workflow.admin", "board.admin", "automation.admin", "report.read", "report.manage",
                    "agent.provider.manage", "agent.provider.credential.manage", "agent.profile.manage", "agent.assign",
                    "agent.task.view", "agent.task.cancel", "agent.task.retry", "agent.task.view_logs",
                    "agent.task.accept_result", "repository_connection.manage"),
            "agent_manager", List.of("workspace.read", "project.read", "work_item.read", "work_item.update", "agent.provider.manage",
                    "agent.provider.credential.manage", "agent.profile.manage", "agent.assign", "agent.task.view",
                    "agent.task.cancel", "agent.task.retry", "agent.task.view_logs", "agent.task.accept_result",
                    "repository_connection.manage"),
            "member", List.of("workspace.read", "project.read", "work_item.create", "work_item.read", "work_item.update",
                    "work_item.transition", "work_item.comment", "work_item.link",
                    "work_log.create_own", "work_log.update_own", "work_log.delete_own", "report.read"),
            "viewer", List.of("workspace.read", "project.read", "work_item.read", "report.read"),
            "project_admin", List.of("project.admin", "project.read", "work_item.create", "work_item.read", "work_item.update",
                    "work_item.delete", "work_item.transition", "work_item.comment", "work_item.link",
                    "work_log.create_own", "work_log.update_own", "work_log.delete_own", "board.admin", "report.read", "report.manage")
    );

    private final ObjectMapper objectMapper;
    private final WorkItemTypeRepository workItemTypeRepository;
    private final WorkItemTypeRuleRepository workItemTypeRuleRepository;
    private final PriorityRepository priorityRepository;
    private final ResolutionRepository resolutionRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final WorkflowTransitionRuleRepository workflowTransitionRuleRepository;
    private final WorkflowTransitionActionRepository workflowTransitionActionRepository;
    private final ProjectWorkItemTypeRepository projectWorkItemTypeRepository;
    private final WorkflowAssignmentRepository workflowAssignmentRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardSwimlaneRepository boardSwimlaneRepository;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final AttachmentStorageConfigRepository attachmentStorageConfigRepository;
    private final String localAttachmentRoot;

    public WorkspaceSeedService(
            ObjectMapper objectMapper,
            WorkItemTypeRepository workItemTypeRepository,
            WorkItemTypeRuleRepository workItemTypeRuleRepository,
            PriorityRepository priorityRepository,
            ResolutionRepository resolutionRepository,
            WorkflowRepository workflowRepository,
            WorkflowStatusRepository workflowStatusRepository,
            WorkflowTransitionRepository workflowTransitionRepository,
            WorkflowTransitionRuleRepository workflowTransitionRuleRepository,
            WorkflowTransitionActionRepository workflowTransitionActionRepository,
            ProjectWorkItemTypeRepository projectWorkItemTypeRepository,
            WorkflowAssignmentRepository workflowAssignmentRepository,
            BoardRepository boardRepository,
            BoardColumnRepository boardColumnRepository,
            BoardSwimlaneRepository boardSwimlaneRepository,
            ProjectSettingsRepository projectSettingsRepository,
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            ProjectMembershipRepository projectMembershipRepository,
            AttachmentStorageConfigRepository attachmentStorageConfigRepository,
            @Value("${trasck.attachments.local-root:./data/attachments}") String localAttachmentRoot
    ) {
        this.objectMapper = objectMapper;
        this.workItemTypeRepository = workItemTypeRepository;
        this.workItemTypeRuleRepository = workItemTypeRuleRepository;
        this.priorityRepository = priorityRepository;
        this.resolutionRepository = resolutionRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.workflowTransitionRuleRepository = workflowTransitionRuleRepository;
        this.workflowTransitionActionRepository = workflowTransitionActionRepository;
        this.projectWorkItemTypeRepository = projectWorkItemTypeRepository;
        this.workflowAssignmentRepository = workflowAssignmentRepository;
        this.boardRepository = boardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.boardSwimlaneRepository = boardSwimlaneRepository;
        this.projectSettingsRepository = projectSettingsRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.projectMembershipRepository = projectMembershipRepository;
        this.attachmentStorageConfigRepository = attachmentStorageConfigRepository;
        this.localAttachmentRoot = localAttachmentRoot;
    }

    InitialSetupResponse.SeedDataSummary seed(Workspace workspace, Project project, User adminUser) {
        Map<String, WorkItemType> workItemTypes = seedWorkItemTypes(workspace.getId());
        List<WorkItemTypeRule> typeRules = seedWorkItemTypeRules(workspace.getId(), workItemTypes);
        List<Priority> priorities = seedPriorities(workspace.getId());
        Map<String, Resolution> resolutions = seedResolutions(workspace.getId());
        Workflow workflow = seedWorkflow(workspace.getId());
        Map<String, WorkflowStatus> statuses = seedWorkflowStatuses(workflow.getId());
        List<WorkflowTransition> transitions = seedWorkflowTransitions(workflow.getId(), statuses, resolutions);
        List<ProjectWorkItemType> projectWorkItemTypes = seedProjectWorkItemTypes(project.getId(), workItemTypes.values());
        List<WorkflowAssignment> workflowAssignments = seedWorkflowAssignments(project.getId(), workflow.getId(), workItemTypes.values());
        Board board = seedBoard(workspace.getId(), project.getId());
        List<BoardColumn> columns = seedBoardColumns(board.getId(), statuses);
        seedBoardSwimlane(board.getId());
        ProjectSettings projectSettings = seedProjectSettings(project.getId(), workflow.getId(), board.getId());
        List<Role> roles = seedRolesAndMemberships(workspace.getId(), project.getId(), adminUser.getId());
        AttachmentStorageConfig attachmentStorageConfig = seedAttachmentStorage(workspace.getId());

        return new InitialSetupResponse.SeedDataSummary(
                keyed(workItemTypes.values()),
                typeRules.stream()
                        .map(rule -> new InitialSetupResponse.TypeRuleSummary(
                                rule.getId(),
                                findTypeKey(workItemTypes, rule.getParentTypeId()),
                                findTypeKey(workItemTypes, rule.getChildTypeId())
                        ))
                        .toList(),
                priorities.stream().map(priority -> new InitialSetupResponse.KeyedId(priority.getId(), priority.getKey(), priority.getName())).toList(),
                resolutions.values().stream().map(resolution -> new InitialSetupResponse.KeyedId(resolution.getId(), resolution.getKey(), resolution.getName())).toList(),
                new InitialSetupResponse.WorkflowSummary(
                        workflow.getId(),
                        workflow.getName(),
                        statuses.values().stream().map(status -> new InitialSetupResponse.KeyedId(status.getId(), status.getKey(), status.getName())).toList(),
                        transitions.stream().map(transition -> new InitialSetupResponse.KeyedId(transition.getId(), transition.getKey(), transition.getName())).toList()
                ),
                new InitialSetupResponse.BoardSummary(
                        board.getId(),
                        board.getName(),
                        columns.stream().map(column -> new InitialSetupResponse.KeyedId(column.getId(), slugKey(column.getName()), column.getName())).toList()
                ),
                roles.stream().map(role -> new InitialSetupResponse.KeyedId(role.getId(), role.getKey(), role.getName())).toList(),
                projectWorkItemTypes.stream()
                        .map(projectType -> new InitialSetupResponse.KeyedId(projectType.getId(), findTypeKey(workItemTypes, projectType.getWorkItemTypeId()), findTypeName(workItemTypes, projectType.getWorkItemTypeId())))
                        .toList(),
                workflowAssignments.stream()
                        .map(assignment -> new InitialSetupResponse.KeyedId(assignment.getId(), findTypeKey(workItemTypes, assignment.getWorkItemTypeId()), "Default workflow for " + findTypeName(workItemTypes, assignment.getWorkItemTypeId())))
                        .toList(),
                new InitialSetupResponse.KeyedId(projectSettings.getProjectId(), "project_settings", "Project Settings"),
                new InitialSetupResponse.KeyedId(attachmentStorageConfig.getId(), "filesystem", attachmentStorageConfig.getName())
        );
    }

    private Map<String, WorkItemType> seedWorkItemTypes(UUID workspaceId) {
        Map<String, WorkItemType> result = new LinkedHashMap<>();
        for (TypeSeed seed : TYPE_SEEDS) {
            WorkItemType type = new WorkItemType();
            type.setWorkspaceId(workspaceId);
            type.setName(seed.name());
            type.setKey(seed.key());
            type.setHierarchyLevel(seed.hierarchyLevel());
            type.setIsDefault(true);
            type.setIsLeaf(seed.leaf());
            type.setEnabled(true);
            type.setIcon(seed.icon());
            type.setColor(seed.color());
            result.put(seed.key(), workItemTypeRepository.save(type));
        }
        return result;
    }

    private List<WorkItemTypeRule> seedWorkItemTypeRules(UUID workspaceId, Map<String, WorkItemType> types) {
        List<WorkItemTypeRule> rules = new ArrayList<>();
        for (TypeRuleSeed seed : TYPE_RULE_SEEDS) {
            WorkItemTypeRule rule = new WorkItemTypeRule();
            rule.setWorkspaceId(workspaceId);
            rule.setParentTypeId(types.get(seed.parent()).getId());
            rule.setChildTypeId(types.get(seed.child()).getId());
            rule.setEnabled(true);
            rules.add(workItemTypeRuleRepository.save(rule));
        }
        return rules;
    }

    private List<Priority> seedPriorities(UUID workspaceId) {
        List<Priority> priorities = new ArrayList<>();
        for (PrioritySeed seed : PRIORITY_SEEDS) {
            Priority priority = new Priority();
            priority.setWorkspaceId(workspaceId);
            priority.setName(seed.name());
            priority.setKey(seed.key());
            priority.setColor(seed.color());
            priority.setSortOrder(seed.sortOrder());
            priority.setIsDefault(seed.defaultPriority());
            priorities.add(priorityRepository.save(priority));
        }
        return priorities;
    }

    private Map<String, Resolution> seedResolutions(UUID workspaceId) {
        Map<String, Resolution> result = new LinkedHashMap<>();
        for (ResolutionSeed seed : RESOLUTION_SEEDS) {
            Resolution resolution = new Resolution();
            resolution.setWorkspaceId(workspaceId);
            resolution.setName(seed.name());
            resolution.setKey(seed.key());
            resolution.setCategory(seed.category());
            resolution.setSortOrder(seed.sortOrder());
            resolution.setIsDefault(seed.defaultResolution());
            result.put(seed.key(), resolutionRepository.save(resolution));
        }
        return result;
    }

    private Workflow seedWorkflow(UUID workspaceId) {
        Workflow workflow = new Workflow();
        workflow.setWorkspaceId(workspaceId);
        workflow.setName("Default Workflow");
        workflow.setDescription("Default workflow with mandatory human approval before Done.");
        workflow.setActive(true);
        return workflowRepository.save(workflow);
    }

    private Map<String, WorkflowStatus> seedWorkflowStatuses(UUID workflowId) {
        Map<String, WorkflowStatus> result = new LinkedHashMap<>();
        for (StatusSeed seed : STATUS_SEEDS) {
            WorkflowStatus status = new WorkflowStatus();
            status.setWorkflowId(workflowId);
            status.setName(seed.name());
            status.setKey(seed.key());
            status.setCategory(seed.category());
            status.setColor(seed.color());
            status.setSortOrder(seed.sortOrder());
            status.setTerminal(seed.terminal());
            result.put(seed.key(), workflowStatusRepository.save(status));
        }
        return result;
    }

    private List<WorkflowTransition> seedWorkflowTransitions(
            UUID workflowId,
            Map<String, WorkflowStatus> statuses,
            Map<String, Resolution> resolutions
    ) {
        List<WorkflowTransition> transitions = new ArrayList<>();
        for (TransitionSeed seed : TRANSITION_SEEDS) {
            WorkflowTransition transition = new WorkflowTransition();
            transition.setWorkflowId(workflowId);
            transition.setFromStatusId(statuses.get(seed.from()).getId());
            transition.setToStatusId(statuses.get(seed.to()).getId());
            transition.setName(seed.name());
            transition.setKey(seed.key());
            transition.setGlobalTransition(false);
            transition.setSortOrder(seed.sortOrder());
            transitions.add(workflowTransitionRepository.save(transition));

            if ("approval_to_done".equals(seed.key())) {
                seedApprovalRuleAndAction(transition.getId(), resolutions.get("done").getId());
            }
        }
        return transitions;
    }

    private void seedApprovalRuleAndAction(UUID transitionId, UUID doneResolutionId) {
        ObjectNode ruleConfig = objectMapper.createObjectNode()
                .put("requiresHumanApproval", true)
                .put("appliesToHumanAndAgentWork", true);
        WorkflowTransitionRule rule = new WorkflowTransitionRule();
        rule.setTransitionId(transitionId);
        rule.setRuleType("human_approval_required");
        rule.setConfig(ruleConfig);
        rule.setErrorMessage("Work must be approved by a human before it can move to Done.");
        rule.setPosition(0);
        rule.setEnabled(true);
        workflowTransitionRuleRepository.save(rule);

        ObjectNode actionConfig = objectMapper.createObjectNode().put("resolutionId", doneResolutionId.toString());
        WorkflowTransitionAction action = new WorkflowTransitionAction();
        action.setTransitionId(transitionId);
        action.setActionType("set_resolution");
        action.setConfig(actionConfig);
        action.setPosition(0);
        action.setEnabled(true);
        workflowTransitionActionRepository.save(action);
    }

    private List<ProjectWorkItemType> seedProjectWorkItemTypes(UUID projectId, Collection<WorkItemType> types) {
        List<ProjectWorkItemType> result = new ArrayList<>();
        for (WorkItemType type : types) {
            ProjectWorkItemType projectType = new ProjectWorkItemType();
            projectType.setProjectId(projectId);
            projectType.setWorkItemTypeId(type.getId());
            projectType.setEnabled(true);
            projectType.setDefaultType("story".equals(type.getKey()));
            result.add(projectWorkItemTypeRepository.save(projectType));
        }
        return result;
    }

    private List<WorkflowAssignment> seedWorkflowAssignments(UUID projectId, UUID workflowId, Collection<WorkItemType> types) {
        List<WorkflowAssignment> result = new ArrayList<>();
        for (WorkItemType type : types) {
            WorkflowAssignment assignment = new WorkflowAssignment();
            assignment.setProjectId(projectId);
            assignment.setWorkItemTypeId(type.getId());
            assignment.setWorkflowId(workflowId);
            assignment.setDefaultForProject(true);
            result.add(workflowAssignmentRepository.save(assignment));
        }
        return result;
    }

    private Board seedBoard(UUID workspaceId, UUID projectId) {
        Board board = new Board();
        board.setWorkspaceId(workspaceId);
        board.setProjectId(projectId);
        board.setName("Default Board");
        board.setType("kanban");
        board.setFilterConfig(objectMapper.createObjectNode().put("projectId", projectId.toString()));
        board.setActive(true);
        return boardRepository.save(board);
    }

    private List<BoardColumn> seedBoardColumns(UUID boardId, Map<String, WorkflowStatus> statuses) {
        return List.of(
                seedBoardColumn(boardId, "Backlog", 10, false, statusIds(statuses, "open")),
                seedBoardColumn(boardId, "Ready", 20, false, statusIds(statuses, "ready")),
                seedBoardColumn(boardId, "In Progress", 30, false, statusIds(statuses, "in_progress", "blocked")),
                seedBoardColumn(boardId, "Review", 40, false, statusIds(statuses, "in_review")),
                seedBoardColumn(boardId, "Approval", 50, false, statusIds(statuses, "approval")),
                seedBoardColumn(boardId, "Done", 60, true, statusIds(statuses, "done"))
        );
    }

    private BoardColumn seedBoardColumn(UUID boardId, String name, int position, boolean done, ArrayNode statusIds) {
        BoardColumn column = new BoardColumn();
        column.setBoardId(boardId);
        column.setName(name);
        column.setPosition(position);
        column.setDoneColumn(done);
        column.setStatusIds(statusIds);
        return boardColumnRepository.save(column);
    }

    private ArrayNode statusIds(Map<String, WorkflowStatus> statuses, String... keys) {
        ArrayNode ids = objectMapper.createArrayNode();
        for (String key : keys) {
            ids.add(statuses.get(key).getId().toString());
        }
        return ids;
    }

    private void seedBoardSwimlane(UUID boardId) {
        BoardSwimlane swimlane = new BoardSwimlane();
        swimlane.setBoardId(boardId);
        swimlane.setName("All Work");
        swimlane.setSwimlaneType("query");
        swimlane.setQuery(objectMapper.createObjectNode());
        swimlane.setPosition(0);
        swimlane.setEnabled(true);
        boardSwimlaneRepository.save(swimlane);
    }

    private ProjectSettings seedProjectSettings(UUID projectId, UUID workflowId, UUID boardId) {
        ProjectSettings settings = new ProjectSettings();
        settings.setProjectId(projectId);
        settings.setDefaultWorkflowId(workflowId);
        settings.setDefaultBoardId(boardId);
        settings.setEstimationUnit("points");
        settings.setCrossProjectLinkingPolicy("links_only");
        settings.setConfig(objectMapper.createObjectNode());
        return projectSettingsRepository.save(settings);
    }

    private List<Role> seedRolesAndMemberships(UUID workspaceId, UUID projectId, UUID adminUserId) {
        Map<String, Permission> permissions = loadPermissions();
        List<Role> roles = new ArrayList<>();
        roles.add(seedWorkspaceRole(workspaceId, "Workspace Owner", "workspace_owner", "Owns all workspace configuration.", permissions));
        roles.add(seedWorkspaceRole(workspaceId, "Workspace Admin", "workspace_admin", "Administers workspace configuration.", permissions));
        roles.add(seedWorkspaceRole(workspaceId, "Agent Manager", "agent_manager", "Configures agents and agent task execution.", permissions));
        roles.add(seedWorkspaceRole(workspaceId, "Member", "member", "Creates and updates project work.", permissions));
        roles.add(seedWorkspaceRole(workspaceId, "Viewer", "viewer", "Reads workspace and project work.", permissions));
        Role projectAdmin = seedProjectRole(workspaceId, projectId, "Project Admin", "project_admin", "Administers one project.", permissions);
        roles.add(projectAdmin);

        WorkspaceMembership workspaceMembership = new WorkspaceMembership();
        workspaceMembership.setWorkspaceId(workspaceId);
        workspaceMembership.setUserId(adminUserId);
        workspaceMembership.setRoleId(roles.get(0).getId());
        workspaceMembership.setStatus("active");
        workspaceMembership.setJoinedAt(OffsetDateTime.now());
        workspaceMembershipRepository.save(workspaceMembership);

        ProjectMembership projectMembership = new ProjectMembership();
        projectMembership.setProjectId(projectId);
        projectMembership.setUserId(adminUserId);
        projectMembership.setRoleId(projectAdmin.getId());
        projectMembership.setStatus("active");
        projectMembershipRepository.save(projectMembership);

        return roles;
    }

    private Role seedWorkspaceRole(UUID workspaceId, String name, String key, String description, Map<String, Permission> permissions) {
        Role role = new Role();
        role.setWorkspaceId(workspaceId);
        role.setName(name);
        role.setKey(key);
        role.setScope("workspace");
        role.setDescription(description);
        role.setSystemRole(true);
        Role saved = roleRepository.save(role);
        seedRolePermissions(saved, permissions);
        return saved;
    }

    private Role seedProjectRole(UUID workspaceId, UUID projectId, String name, String key, String description, Map<String, Permission> permissions) {
        Role role = new Role();
        role.setWorkspaceId(workspaceId);
        role.setProjectId(projectId);
        role.setName(name);
        role.setKey(key);
        role.setScope("project");
        role.setDescription(description);
        role.setSystemRole(true);
        Role saved = roleRepository.save(role);
        seedRolePermissions(saved, permissions);
        return saved;
    }

    private void seedRolePermissions(Role role, Map<String, Permission> permissions) {
        List<String> permissionKeys = ROLE_PERMISSIONS.get(role.getKey());
        if (permissionKeys == null) {
            return;
        }
        Collection<Permission> rolePermissions = permissionKeys.contains("*") ? permissions.values() : permissionKeys.stream()
                .map(permissions::get)
                .toList();
        List<RolePermission> joins = rolePermissions.stream()
                .map(permission -> {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setId(new RolePermissionId(role.getId(), permission.getId()));
                    return rolePermission;
                })
                .toList();
        rolePermissionRepository.saveAll(joins);
    }

    private Map<String, Permission> loadPermissions() {
        List<String> requiredKeys = ROLE_PERMISSIONS.values().stream()
                .flatMap(List::stream)
                .filter(key -> !"*".equals(key))
                .distinct()
                .toList();
        Map<String, Permission> permissions = permissionRepository.findByKeyIn(requiredKeys).stream()
                .collect(Collectors.toMap(Permission::getKey, Function.identity()));
        List<Permission> allPermissions = permissionRepository.findAll();
        permissions.putAll(allPermissions.stream().collect(Collectors.toMap(Permission::getKey, Function.identity())));
        for (String requiredKey : requiredKeys) {
            if (!permissions.containsKey(requiredKey)) {
                throw new IllegalStateException("Missing permission seed: " + requiredKey);
            }
        }
        return permissions;
    }

    private AttachmentStorageConfig seedAttachmentStorage(UUID workspaceId) {
        ObjectNode config = objectMapper.createObjectNode()
                .put("rootPath", localAttachmentRoot)
                .put("createDirectories", true);
        AttachmentStorageConfig storageConfig = new AttachmentStorageConfig();
        storageConfig.setWorkspaceId(workspaceId);
        storageConfig.setName("Local Filesystem");
        storageConfig.setProvider("filesystem");
        storageConfig.setConfig(config);
        storageConfig.setActive(true);
        storageConfig.setDefaultConfig(true);
        return attachmentStorageConfigRepository.save(storageConfig);
    }

    private List<InitialSetupResponse.KeyedId> keyed(Collection<WorkItemType> types) {
        return types.stream()
                .map(type -> new InitialSetupResponse.KeyedId(type.getId(), type.getKey(), type.getName()))
                .toList();
    }

    private String findTypeKey(Map<String, WorkItemType> types, UUID typeId) {
        return types.values().stream()
                .filter(type -> type.getId().equals(typeId))
                .map(WorkItemType::getKey)
                .findFirst()
                .orElseThrow();
    }

    private String findTypeName(Map<String, WorkItemType> types, UUID typeId) {
        return types.values().stream()
                .filter(type -> type.getId().equals(typeId))
                .map(WorkItemType::getName)
                .findFirst()
                .orElseThrow();
    }

    private String slugKey(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private record TypeSeed(String name, String key, int hierarchyLevel, boolean leaf, String icon, String color) {
    }

    private record TypeRuleSeed(String parent, String child) {
    }

    private record PrioritySeed(String name, String key, String color, int sortOrder, boolean defaultPriority) {
    }

    private record ResolutionSeed(String name, String key, String category, int sortOrder, boolean defaultResolution) {
    }

    private record StatusSeed(String name, String key, String category, String color, int sortOrder, boolean terminal) {
    }

    private record TransitionSeed(String from, String to, String name, String key, int sortOrder) {
    }
}
