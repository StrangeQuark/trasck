package com.strangequark.trasck.setup;

import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSetupService {

    private final ProjectRepository projectRepository;

    public ProjectSetupService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    Project createProject(InitialSetupRequest.ProjectRequest request, UUID workspaceId, UUID leadUserId) {
        InitialSetupRequest.ProjectRequest projectRequest = SetupRequestValidator.required(request, "project");
        String name = SetupRequestValidator.requiredText(projectRequest.name(), "project.name");
        String key = SetupRequestValidator.key(projectRequest.key(), "project.key");
        String visibility = normalizeVisibility(projectRequest.visibility());

        if (projectRepository.existsByWorkspaceIdAndKeyIgnoreCase(workspaceId, key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A project with this key already exists in the workspace");
        }

        Project project = new Project();
        project.setWorkspaceId(workspaceId);
        project.setName(name);
        project.setKey(key);
        project.setDescription(projectRequest.description());
        project.setVisibility(visibility);
        project.setStatus("active");
        project.setLeadUserId(leadUserId);
        return projectRepository.save(project);
    }

    private String normalizeVisibility(String visibility) {
        String normalized = SetupRequestValidator.optionalText(visibility, "private").toLowerCase();
        if (!normalized.equals("private") && !normalized.equals("workspace") && !normalized.equals("public")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project.visibility must be private, workspace, or public");
        }
        return normalized;
    }
}
