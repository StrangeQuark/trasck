package com.strangequark.trasck.setup;

import com.strangequark.trasck.access.SystemAdmin;
import com.strangequark.trasck.access.SystemAdminRepository;
import com.strangequark.trasck.identity.User;
import com.strangequark.trasck.identity.UserRepository;
import com.strangequark.trasck.organization.Organization;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.workspace.Workspace;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InitialSetupService {

    private final IdentitySetupService identitySetupService;
    private final OrganizationSetupService organizationSetupService;
    private final WorkspaceSetupService workspaceSetupService;
    private final ProjectSetupService projectSetupService;
    private final WorkspaceSeedService workspaceSeedService;
    private final UserRepository userRepository;
    private final SystemAdminRepository systemAdminRepository;

    public InitialSetupService(
            IdentitySetupService identitySetupService,
            OrganizationSetupService organizationSetupService,
            WorkspaceSetupService workspaceSetupService,
            ProjectSetupService projectSetupService,
            WorkspaceSeedService workspaceSeedService,
            UserRepository userRepository,
            SystemAdminRepository systemAdminRepository
    ) {
        this.identitySetupService = identitySetupService;
        this.organizationSetupService = organizationSetupService;
        this.workspaceSetupService = workspaceSetupService;
        this.projectSetupService = projectSetupService;
        this.workspaceSeedService = workspaceSeedService;
        this.userRepository = userRepository;
        this.systemAdminRepository = systemAdminRepository;
    }

    @Transactional
    public InitialSetupResponse createInitialSetup(InitialSetupRequest request) {
        InitialSetupRequest setupRequest = SetupRequestValidator.required(request, "request");
        if (userRepository.count() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Initial setup has already been completed");
        }
        User adminUser = identitySetupService.createAdminUser(setupRequest.adminUser());
        Organization organization = organizationSetupService.createOrganization(setupRequest.organization(), adminUser.getId());
        Workspace workspace = workspaceSetupService.createWorkspace(setupRequest.workspace(), organization.getId());
        Project project = projectSetupService.createProject(setupRequest.project(), workspace.getId(), adminUser.getId());
        grantSystemAdmin(adminUser);
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

    private void grantSystemAdmin(User adminUser) {
        SystemAdmin systemAdmin = new SystemAdmin();
        systemAdmin.setUserId(adminUser.getId());
        systemAdmin.setActive(true);
        systemAdmin.setGrantedById(adminUser.getId());
        systemAdmin.setGrantedAt(OffsetDateTime.now());
        systemAdminRepository.save(systemAdmin);
    }
}
