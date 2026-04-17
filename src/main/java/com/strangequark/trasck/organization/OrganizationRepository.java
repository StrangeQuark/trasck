package com.strangequark.trasck.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsBySlugIgnoreCase(String slug);

    Optional<Organization> findBySlugIgnoreCase(String slug);
}
