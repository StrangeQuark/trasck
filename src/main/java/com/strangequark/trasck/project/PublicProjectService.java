package com.strangequark.trasck.project;

import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;

    public PublicProjectService(ProjectRepository projectRepository, WorkspaceRepository workspaceRepository) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public PublicProjectResponse getPublicProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(this::publicProjectNotFound);
        Workspace workspace = workspaceRepository.findById(project.getWorkspaceId())
                .orElseThrow(this::publicProjectNotFound);

        if (!isPubliclyReadable(workspace, project)) {
            throw publicProjectNotFound();
        }

        return new PublicProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getKey(),
                project.getDescription(),
                project.getVisibility()
        );
    }

    private boolean isPubliclyReadable(Workspace workspace, Project project) {
        return Boolean.TRUE.equals(workspace.getAnonymousReadEnabled())
                && "active".equals(workspace.getStatus())
                && workspace.getDeletedAt() == null
                && "public".equalsIgnoreCase(project.getVisibility())
                && "active".equals(project.getStatus())
                && project.getDeletedAt() == null;
    }

    private ResponseStatusException publicProjectNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Public project not found");
    }
}
