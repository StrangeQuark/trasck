package com.strangequark.trasck.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.identity.UserRepository;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TeamService {

    private final ObjectMapper objectMapper;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public TeamService(
            ObjectMapper objectMapper,
            TeamRepository teamRepository,
            TeamMembershipRepository teamMembershipRepository,
            ProjectTeamRepository projectTeamRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return teamRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        UUID actorId = currentUserService.requireUserId();
        Team team = team(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "workspace.read");
        return TeamResponse.from(team);
    }

    @Transactional
    public TeamResponse createTeam(UUID workspaceId, TeamRequest request) {
        TeamRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        String name = requiredText(createRequest.name(), "name");
        if (teamRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Team name already exists in this workspace");
        }
        UUID leadUserId = validateWorkspaceUser(workspaceId, createRequest.leadUserId(), "leadUserId");
        Team team = new Team();
        team.setWorkspaceId(workspaceId);
        team.setName(name);
        team.setDescription(createRequest.description());
        team.setLeadUserId(leadUserId);
        team.setDefaultCapacity(nonNegative(createRequest.defaultCapacity(), "defaultCapacity"));
        team.setStatus(normalizeStatus(createRequest.status()));
        Team saved = teamRepository.save(team);
        recordTeamEvent(saved, "team.created", actorId);
        return TeamResponse.from(saved);
    }

    @Transactional
    public TeamResponse updateTeam(UUID teamId, TeamRequest request) {
        TeamRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Team team = team(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "workspace.admin");
        if (hasText(updateRequest.name())) {
            String name = updateRequest.name().trim();
            if (!name.equalsIgnoreCase(team.getName())
                    && teamRepository.existsByWorkspaceIdAndNameIgnoreCase(team.getWorkspaceId(), name)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Team name already exists in this workspace");
            }
            team.setName(name);
        }
        if (updateRequest.description() != null) {
            team.setDescription(updateRequest.description());
        }
        if (updateRequest.leadUserId() != null) {
            team.setLeadUserId(validateWorkspaceUser(team.getWorkspaceId(), updateRequest.leadUserId(), "leadUserId"));
        }
        if (updateRequest.defaultCapacity() != null) {
            team.setDefaultCapacity(nonNegative(updateRequest.defaultCapacity(), "defaultCapacity"));
        }
        if (updateRequest.status() != null) {
            team.setStatus(normalizeStatus(updateRequest.status()));
        }
        Team saved = teamRepository.save(team);
        recordTeamEvent(saved, "team.updated", actorId);
        return TeamResponse.from(saved);
    }

    @Transactional
    public void archiveTeam(UUID teamId) {
        UUID actorId = currentUserService.requireUserId();
        Team team = team(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "workspace.admin");
        if (!"archived".equals(team.getStatus())) {
            team.setStatus("archived");
            Team saved = teamRepository.save(team);
            recordTeamEvent(saved, "team.archived", actorId);
        }
    }

    @Transactional(readOnly = true)
    public List<TeamMembershipResponse> listMemberships(UUID teamId) {
        UUID actorId = currentUserService.requireUserId();
        Team team = team(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "workspace.read");
        return teamMembershipRepository.findByTeamIdOrderByJoinedAtAsc(team.getId()).stream()
                .map(TeamMembershipResponse::from)
                .toList();
    }

    @Transactional
    public TeamMembershipResponse upsertMembership(UUID teamId, TeamMembershipRequest request) {
        TeamMembershipRequest membershipRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Team team = activeTeam(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "workspace.admin");
        UUID userId = validateWorkspaceUser(team.getWorkspaceId(), required(membershipRequest.userId(), "userId"), "userId");
        TeamMembership membership = teamMembershipRepository.findByTeamIdAndUserId(team.getId(), userId)
                .orElseGet(TeamMembership::new);
        boolean created = membership.getId() == null;
        membership.setTeamId(team.getId());
        membership.setUserId(userId);
        membership.setRole(normalizeRole(firstText(membershipRequest.role(), "member")));
        membership.setCapacityPercent(positive(firstNonNull(membershipRequest.capacityPercent(), 100), "capacityPercent"));
        if (created || membership.getLeftAt() != null) {
            membership.setJoinedAt(OffsetDateTime.now());
        }
        membership.setLeftAt(null);
        TeamMembership saved = teamMembershipRepository.save(membership);
        recordMembershipEvent(team, saved, created ? "team.membership_added" : "team.membership_updated", actorId);
        return TeamMembershipResponse.from(saved);
    }

    @Transactional
    public void removeMembership(UUID teamId, UUID userId) {
        UUID actorId = currentUserService.requireUserId();
        Team team = team(teamId);
        permissionService.requireWorkspacePermission(actorId, team.getWorkspaceId(), "workspace.admin");
        TeamMembership membership = teamMembershipRepository.findByTeamIdAndUserId(team.getId(), userId)
                .orElseThrow(() -> notFound("Team membership not found"));
        if (membership.getLeftAt() == null) {
            membership.setLeftAt(OffsetDateTime.now());
            TeamMembership saved = teamMembershipRepository.save(membership);
            recordMembershipEvent(team, saved, "team.membership_removed", actorId);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectTeamResponse> listProjectTeams(UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.read");
        return projectTeamRepository.findByIdProjectIdOrderByCreatedAtAsc(project.getId()).stream()
                .map(ProjectTeamResponse::from)
                .toList();
    }

    @Transactional
    public ProjectTeamResponse assignProjectTeam(UUID projectId, UUID teamId, ProjectTeamRequest request) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.admin");
        Team team = activeTeam(teamId);
        if (!project.getWorkspaceId().equals(team.getWorkspaceId())) {
            throw badRequest("Team does not belong to this project workspace");
        }
        ProjectTeam projectTeam = projectTeamRepository.findById(new ProjectTeamId(project.getId(), team.getId()))
                .orElseGet(ProjectTeam::new);
        boolean created = projectTeam.getId().getProjectId() == null;
        projectTeam.setId(new ProjectTeamId(project.getId(), team.getId()));
        projectTeam.setRole(normalizeRole(request == null ? "delivery" : firstText(request.role(), "delivery")));
        ProjectTeam saved = projectTeamRepository.save(projectTeam);
        recordProjectTeamEvent(project, team, saved, created ? "project_team.assigned" : "project_team.updated", actorId);
        return ProjectTeamResponse.from(saved);
    }

    @Transactional
    public void removeProjectTeam(UUID projectId, UUID teamId) {
        UUID actorId = currentUserService.requireUserId();
        Project project = activeProject(projectId);
        permissionService.requireProjectPermission(actorId, project.getId(), "project.admin");
        ProjectTeam projectTeam = projectTeamRepository.findById(new ProjectTeamId(project.getId(), teamId))
                .orElseThrow(() -> notFound("Project team assignment not found"));
        projectTeamRepository.delete(projectTeam);
        Team team = teamRepository.findById(teamId).orElse(null);
        recordProjectTeamEvent(project, team, projectTeam, "project_team.removed", actorId);
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project activeProject(UUID projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> notFound("Project not found"));
        if (project.getDeletedAt() != null || !"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        return project;
    }

    private Team team(UUID teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> notFound("Team not found"));
    }

    private Team activeTeam(UUID teamId) {
        Team team = team(teamId);
        if (!"active".equals(team.getStatus())) {
            throw badRequest("Team is not active");
        }
        return team;
    }

    private UUID validateWorkspaceUser(UUID workspaceId, UUID userId, String fieldName) {
        if (userId == null) {
            return null;
        }
        if (!userRepository.existsById(userId)
                || !workspaceMembershipRepository.existsByWorkspaceIdAndUserIdAndStatusIgnoreCase(workspaceId, userId, "active")) {
            throw badRequest(fieldName + " must be an active workspace member");
        }
        return userId;
    }

    private void recordTeamEvent(Team team, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("teamId", team.getId().toString())
                .put("teamName", team.getName())
                .put("status", team.getStatus())
                .put("actorUserId", actorId.toString());
        domainEventService.record(team.getWorkspaceId(), "team", team.getId(), eventType, payload);
    }

    private void recordMembershipEvent(Team team, TeamMembership membership, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("teamId", team.getId().toString())
                .put("teamName", team.getName())
                .put("userId", membership.getUserId().toString())
                .put("role", membership.getRole())
                .put("actorUserId", actorId.toString());
        domainEventService.record(team.getWorkspaceId(), "team", team.getId(), eventType, payload);
    }

    private void recordProjectTeamEvent(Project project, Team team, ProjectTeam projectTeam, String eventType, UUID actorId) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("projectId", project.getId().toString())
                .put("teamId", projectTeam.getId().getTeamId().toString())
                .put("role", projectTeam.getRole())
                .put("actorUserId", actorId.toString());
        if (team != null) {
            payload.put("teamName", team.getName());
        }
        domainEventService.record(project.getWorkspaceId(), "project_team", project.getId(), eventType, payload);
    }

    private String normalizeStatus(String status) {
        String normalized = hasText(status) ? status.trim().toLowerCase() : "active";
        if (!List.of("active", "inactive", "archived").contains(normalized)) {
            throw badRequest("status must be active, inactive, or archived");
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        String normalized = requiredText(role, "role").toLowerCase().replace(' ', '_').replace('-', '_');
        if (normalized.length() > 80) {
            throw badRequest("role must be 80 characters or fewer");
        }
        return normalized;
    }

    private Integer nonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
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

    private Integer firstNonNull(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
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
