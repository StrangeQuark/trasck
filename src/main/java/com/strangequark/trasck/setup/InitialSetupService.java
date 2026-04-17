package com.strangequark.trasck.setup;

import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.organization.Organization;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.workspace.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialSetupService {

    private final IdentitySetupService identitySetupService;
    private final OrganizationSetupService organizationSetupService;
    private final WorkspaceSetupService workspaceSetupService;
    private final ProjectSetupService projectSetupService;
    private final WorkspaceSeedService workspaceSeedService;

    public InitialSetupService(
            IdentitySetupService identitySetupService,
            OrganizationSetupService organizationSetupService,
            WorkspaceSetupService workspaceSetupService,
            ProjectSetupService projectSetupService,
            WorkspaceSeedService workspaceSeedService
    ) {
        this.identitySetupService = identitySetupService;
        this.organizationSetupService = organizationSetupService;
        this.workspaceSetupService = workspaceSetupService;
        this.projectSetupService = projectSetupService;
        this.workspaceSeedService = workspaceSeedService;
    }

    @Transactional
    public InitialSetupResponse createInitialSetup(InitialSetupRequest request) {
        InitialSetupRequest setupRequest = SetupRequestValidator.required(request, "request");
        User adminUser = identitySetupService.createAdminUser(setupRequest.adminUser());
        Organization organization = organizationSetupService.createOrganization(setupRequest.organization(), adminUser.getId());
        Workspace workspace = workspaceSetupService.createWorkspace(setupRequest.workspace(), organization.getId());
        Project project = projectSetupService.createProject(setupRequest.project(), workspace.getId(), adminUser.getId());
        InitialSetupResponse.SeedDataSummary seedData = workspaceSeedService.seed(workspace, project, adminUser);

        return new InitialSetupResponse(
                new InitialSetupResponse.UserSummary(
                        adminUser.getId(),
                        adminUser.getEmail(),
                        adminUser.getUsername(),
                        adminUser.getDisplayName(),
                        adminUser.getAccountType()
                ),
                new InitialSetupResponse.OrganizationSummary(
                        organization.getId(),
                        organization.getName(),
                        organization.getSlug()
                ),
                new InitialSetupResponse.WorkspaceSummary(
                        workspace.getId(),
                        workspace.getOrganizationId(),
                        workspace.getName(),
                        workspace.getKey(),
                        workspace.getAnonymousReadEnabled()
                ),
                new InitialSetupResponse.ProjectSummary(
                        project.getId(),
                        project.getWorkspaceId(),
                        project.getName(),
                        project.getKey(),
                        project.getVisibility()
                ),
                seedData
        );
    }
}
