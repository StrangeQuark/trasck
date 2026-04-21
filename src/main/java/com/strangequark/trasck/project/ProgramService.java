package com.strangequark.trasck.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.PermissionService;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.identity.CurrentUserService;
import com.strangequark.trasck.workspace.Workspace;
import com.strangequark.trasck.workspace.WorkspaceRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProgramService {

    private final ObjectMapper objectMapper;
    private final ProgramRepository programRepository;
    private final ProgramProjectRepository programProjectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final DomainEventService domainEventService;

    public ProgramService(
            ObjectMapper objectMapper,
            ProgramRepository programRepository,
            ProgramProjectRepository programProjectRepository,
            WorkspaceRepository workspaceRepository,
            ProjectRepository projectRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService,
            DomainEventService domainEventService
    ) {
        this.objectMapper = objectMapper;
        this.programRepository = programRepository;
        this.programProjectRepository = programProjectRepository;
        this.workspaceRepository = workspaceRepository;
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.domainEventService = domainEventService;
    }

    @Transactional(readOnly = true)
    public List<ProgramResponse> listPrograms(UUID workspaceId) {
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.read");
        return programRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(this::programResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramResponse getProgram(UUID programId) {
        UUID actorId = currentUserService.requireUserId();
        Program program = program(programId);
        permissionService.requireWorkspacePermission(actorId, program.getWorkspaceId(), "workspace.read");
        return programResponse(program);
    }

    @Transactional
    public ProgramResponse createProgram(UUID workspaceId, ProgramRequest request) {
        ProgramRequest createRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        activeWorkspace(workspaceId);
        permissionService.requireWorkspacePermission(actorId, workspaceId, "workspace.admin");
        String name = requiredText(createRequest.name(), "name", 160);
        if (programRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw conflict("Program name already exists in this workspace");
        }
        Program program = new Program();
        program.setWorkspaceId(workspaceId);
        program.setName(name);
        program.setDescription(createRequest.description());
        program.setStatus(normalizeStatus(createRequest.status(), "active"));
        program.setRoadmapConfig(toJsonObject(createRequest.roadmapConfig(), "roadmapConfig"));
        program.setReportConfig(toJsonObject(createRequest.reportConfig(), "reportConfig"));
        Program saved = programRepository.save(program);
        recordProgramEvent(saved, "program.created", actorId);
        return programResponse(saved);
    }

    @Transactional
    public ProgramResponse updateProgram(UUID programId, ProgramRequest request) {
        ProgramRequest updateRequest = required(request, "request");
        UUID actorId = currentUserService.requireUserId();
        Program program = program(programId);
        permissionService.requireWorkspacePermission(actorId, program.getWorkspaceId(), "workspace.admin");
        if (hasText(updateRequest.name())) {
            String name = requiredText(updateRequest.name(), "name", 160);
            if (!name.equalsIgnoreCase(program.getName())
                    && programRepository.existsByWorkspaceIdAndNameIgnoreCase(program.getWorkspaceId(), name)) {
                throw conflict("Program name already exists in this workspace");
            }
            program.setName(name);
        }
        if (updateRequest.description() != null) {
            program.setDescription(updateRequest.description());
        }
        if (updateRequest.status() != null) {
            program.setStatus(normalizeStatus(updateRequest.status(), null));
        }
        if (updateRequest.roadmapConfig() != null) {
            program.setRoadmapConfig(toJsonObject(updateRequest.roadmapConfig(), "roadmapConfig"));
        }
        if (updateRequest.reportConfig() != null) {
            program.setReportConfig(toJsonObject(updateRequest.reportConfig(), "reportConfig"));
        }
        Program saved = programRepository.save(program);
        recordProgramEvent(saved, "program.updated", actorId);
        return programResponse(saved);
    }

    @Transactional
    public void archiveProgram(UUID programId) {
        UUID actorId = currentUserService.requireUserId();
        Program program = program(programId);
        permissionService.requireWorkspacePermission(actorId, program.getWorkspaceId(), "workspace.admin");
        if (!"archived".equals(program.getStatus())) {
            program.setStatus("archived");
            programRepository.save(program);
            recordProgramEvent(program, "program.archived", actorId);
        }
    }

    @Transactional(readOnly = true)
    public List<ProgramProjectResponse> listProgramProjects(UUID programId) {
        UUID actorId = currentUserService.requireUserId();
        Program program = activeProgram(programId);
        permissionService.requireWorkspacePermission(actorId, program.getWorkspaceId(), "workspace.read");
        return programProjectRepository.findByIdProgramIdOrderByPositionAscCreatedAtAsc(program.getId()).stream()
                .map(ProgramProjectResponse::from)
                .toList();
    }

    @Transactional
    public ProgramProjectResponse assignProgramProject(UUID programId, UUID projectId, ProgramProjectRequest request) {
        UUID actorId = currentUserService.requireUserId();
        Program program = activeProgram(programId);
        permissionService.requireWorkspacePermission(actorId, program.getWorkspaceId(), "workspace.admin");
        Project project = activeWorkspaceProject(program.getWorkspaceId(), projectId);
        ProgramProject programProject = programProjectRepository
                .findById(new ProgramProjectId(program.getId(), project.getId()))
                .orElseGet(ProgramProject::new);
        programProject.setId(new ProgramProjectId(program.getId(), project.getId()));
        programProject.setPosition(nonNegative(request == null ? null : request.position(), "position"));
        ProgramProject saved = programProjectRepository.save(programProject);
        recordProgramProjectEvent(program, project.getId(), "program.project_assigned", actorId);
        return ProgramProjectResponse.from(saved);
    }

    @Transactional
    public void removeProgramProject(UUID programId, UUID projectId) {
        UUID actorId = currentUserService.requireUserId();
        Program program = activeProgram(programId);
        permissionService.requireWorkspacePermission(actorId, program.getWorkspaceId(), "workspace.admin");
        Project project = activeWorkspaceProject(program.getWorkspaceId(), projectId);
        if (!programProjectRepository.existsByIdProgramIdAndIdProjectId(program.getId(), project.getId())) {
            throw notFound("Program project assignment not found");
        }
        programProjectRepository.deleteByIdProgramIdAndIdProjectId(program.getId(), project.getId());
        recordProgramProjectEvent(program, project.getId(), "program.project_removed", actorId);
    }

    private ProgramResponse programResponse(Program program) {
        return ProgramResponse.from(program, programProjectRepository.findByIdProgramIdOrderByPositionAscCreatedAtAsc(program.getId()));
    }

    private Workspace activeWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(required(workspaceId, "workspaceId"))
                .orElseThrow(() -> notFound("Workspace not found"));
        if (workspace.getDeletedAt() != null || !"active".equals(workspace.getStatus())) {
            throw notFound("Workspace not found");
        }
        return workspace;
    }

    private Project activeWorkspaceProject(UUID workspaceId, UUID projectId) {
        Project project = projectRepository.findById(required(projectId, "projectId"))
                .orElseThrow(() -> notFound("Project not found"));
        if (!workspaceId.equals(project.getWorkspaceId())
                || project.getDeletedAt() != null
                || !"active".equals(project.getStatus())) {
            throw notFound("Project not found");
        }
        return project;
    }

    private Program activeProgram(UUID programId) {
        Program program = program(programId);
        if (!"active".equals(program.getStatus())) {
            throw notFound("Program not found");
        }
        return program;
    }

    private Program program(UUID programId) {
        Program program = programRepository.findById(required(programId, "programId"))
                .orElseThrow(() -> notFound("Program not found"));
        activeWorkspace(program.getWorkspaceId());
        return program;
    }

    private void recordProgramEvent(Program program, String eventType, UUID actorId) {
        ObjectNode payload = programPayload(program, actorId);
        domainEventService.record(program.getWorkspaceId(), "program", program.getId(), eventType, payload);
    }

    private void recordProgramProjectEvent(Program program, UUID projectId, String eventType, UUID actorId) {
        ObjectNode payload = programPayload(program, actorId).put("projectId", projectId.toString());
        domainEventService.record(program.getWorkspaceId(), "program", program.getId(), eventType, payload);
    }

    private ObjectNode programPayload(Program program, UUID actorId) {
        return objectMapper.createObjectNode()
                .put("programId", program.getId().toString())
                .put("workspaceId", program.getWorkspaceId().toString())
                .put("name", program.getName())
                .put("status", program.getStatus())
                .put("actorUserId", actorId.toString());
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

    private JsonNode toJsonObject(Object value, String fieldName) {
        JsonNode json = toJsonNullable(value);
        if (json == null || json.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!json.isObject()) {
            throw badRequest(fieldName + " must be a JSON object");
        }
        return json;
    }

    private Integer nonNegative(Integer value, String fieldName) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw badRequest(fieldName + " must be greater than or equal to zero");
        }
        return value;
    }

    private String normalizeStatus(String status, String defaultStatus) {
        if (!hasText(status)) {
            if (defaultStatus != null) {
                return defaultStatus;
            }
            throw badRequest("status is required");
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!List.of("active", "archived").contains(normalized)) {
            throw badRequest("status must be active or archived");
        }
        return normalized;
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw badRequest(fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        return requiredText(value, fieldName, null);
    }

    private String requiredText(String value, String fieldName, Integer maxLength) {
        if (!hasText(value)) {
            throw badRequest(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (maxLength != null && trimmed.length() > maxLength) {
            throw badRequest(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return trimmed;
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

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
