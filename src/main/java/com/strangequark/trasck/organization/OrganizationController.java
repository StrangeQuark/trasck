package com.strangequark.trasck.organization;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {

    private final OrganizationManagementService organizationManagementService;

    public OrganizationController(OrganizationManagementService organizationManagementService) {
        this.organizationManagementService = organizationManagementService;
    }

    @GetMapping("/organizations")
    public List<OrganizationResponse> listOrganizations() {
        return organizationManagementService.listOrganizations();
    }

    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(@RequestBody OrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationManagementService.createOrganization(request));
    }

    @GetMapping("/organizations/{organizationId}")
    public OrganizationResponse getOrganization(@PathVariable UUID organizationId) {
        return organizationManagementService.getOrganization(organizationId);
    }

    @PatchMapping("/organizations/{organizationId}")
    public OrganizationResponse updateOrganization(
            @PathVariable UUID organizationId,
            @RequestBody OrganizationRequest request
    ) {
        return organizationManagementService.updateOrganization(organizationId, request);
    }

    @DeleteMapping("/organizations/{organizationId}")
    public ResponseEntity<Void> archiveOrganization(@PathVariable UUID organizationId) {
        organizationManagementService.archiveOrganization(organizationId);
        return ResponseEntity.noContent().build();
    }
}
