package com.strangequark.trasck.setup;

import com.strangequark.trasck.organization.Organization;
import com.strangequark.trasck.organization.OrganizationRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationSetupService {

    private final OrganizationRepository organizationRepository;

    public OrganizationSetupService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    Organization createOrganization(InitialSetupRequest.OrganizationRequest request, UUID createdById) {
        InitialSetupRequest.OrganizationRequest organizationRequest = SetupRequestValidator.required(request, "organization");
        String name = SetupRequestValidator.requiredText(organizationRequest.name(), "organization.name");
        String slug = SetupRequestValidator.slug(organizationRequest.slug(), "organization.slug");

        if (organizationRepository.existsBySlugIgnoreCase(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An organization with this slug already exists");
        }

        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(slug);
        organization.setStatus("active");
        organization.setCreatedById(createdById);
        return organizationRepository.save(organization);
    }
}
