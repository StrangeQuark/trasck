package com.strangequark.trasck.team;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/workspaces/{workspaceId}/teams")
    public List<TeamResponse> listTeams(@PathVariable UUID workspaceId) {
        return teamService.listTeams(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/teams")
    public ResponseEntity<TeamResponse> createTeam(@PathVariable UUID workspaceId, @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(workspaceId, request));
    }

    @GetMapping("/teams/{teamId}")
    public TeamResponse getTeam(@PathVariable UUID teamId) {
        return teamService.getTeam(teamId);
    }

    @PatchMapping("/teams/{teamId}")
    public TeamResponse updateTeam(@PathVariable UUID teamId, @RequestBody TeamRequest request) {
        return teamService.updateTeam(teamId, request);
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<Void> archiveTeam(@PathVariable UUID teamId) {
        teamService.archiveTeam(teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teams/{teamId}/memberships")
    public List<TeamMembershipResponse> listMemberships(@PathVariable UUID teamId) {
        return teamService.listMemberships(teamId);
    }

    @PostMapping("/teams/{teamId}/memberships")
    public ResponseEntity<TeamMembershipResponse> upsertMembership(
            @PathVariable UUID teamId,
            @RequestBody TeamMembershipRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.upsertMembership(teamId, request));
    }

    @DeleteMapping("/teams/{teamId}/memberships/{userId}")
    public ResponseEntity<Void> removeMembership(@PathVariable UUID teamId, @PathVariable UUID userId) {
        teamService.removeMembership(teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/teams")
    public List<ProjectTeamResponse> listProjectTeams(@PathVariable UUID projectId) {
        return teamService.listProjectTeams(projectId);
    }

    @PutMapping("/projects/{projectId}/teams/{teamId}")
    public ProjectTeamResponse assignProjectTeam(
            @PathVariable UUID projectId,
            @PathVariable UUID teamId,
            @RequestBody(required = false) ProjectTeamRequest request
    ) {
        return teamService.assignProjectTeam(projectId, teamId, request);
    }

    @DeleteMapping("/projects/{projectId}/teams/{teamId}")
    public ResponseEntity<Void> removeProjectTeam(@PathVariable UUID projectId, @PathVariable UUID teamId) {
        teamService.removeProjectTeam(projectId, teamId);
        return ResponseEntity.noContent().build();
    }
}
