package com.strangequark.trasck.event;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DomainEventAdminController {

    private final DomainEventAdminService domainEventAdminService;

    public DomainEventAdminController(DomainEventAdminService domainEventAdminService) {
        this.domainEventAdminService = domainEventAdminService;
    }

    @PostMapping("/workspaces/{workspaceId}/domain-events/replay")
    @PreAuthorize("@permissionService.canManageWorkspace(authentication, #workspaceId)")
    public DomainEventReplayResponse replay(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) DomainEventReplayRequest request
    ) {
        return domainEventAdminService.replay(workspaceId, request);
    }
}
