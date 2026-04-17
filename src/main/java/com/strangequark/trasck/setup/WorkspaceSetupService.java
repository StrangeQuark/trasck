package com.strangequark.trasck.setup;

import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceSetupService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceSetupService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    Workspace createWorkspace(InitialSetupRequest.WorkspaceRequest request, UUID organizationId) {
        InitialSetupRequest.WorkspaceRequest workspaceRequest = SetupRequestValidator.required(request, "workspace");
        String name = SetupRequestValidator.requiredText(workspaceRequest.name(), "workspace.name");
        String key = SetupRequestValidator.key(workspaceRequest.key(), "workspace.key");

        if (workspaceRepository.existsByOrganizationIdAndKeyIgnoreCase(organizationId, key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A workspace with this key already exists in the organization");
        }

        Workspace workspace = new Workspace();
        workspace.setOrganizationId(organizationId);
        workspace.setName(name);
        workspace.setKey(key);
        workspace.setTimezone(SetupRequestValidator.optionalText(workspaceRequest.timezone(), "UTC"));
        workspace.setLocale(SetupRequestValidator.optionalText(workspaceRequest.locale(), "en-US"));
        workspace.setStatus("active");
        workspace.setAnonymousReadEnabled(Boolean.TRUE.equals(workspaceRequest.anonymousReadEnabled()));
        return workspaceRepository.save(workspace);
    }
}
