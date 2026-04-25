package com.strangequark.trasck.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsBySlugIgnoreCase(String slug);

    Optional<Organization> findBySlugIgnoreCase(String slug);

    List<Organization> findByDeletedAtIsNullOrderByNameAscSlugAsc();

    List<Organization> findByCreatedByIdAndDeletedAtIsNullOrderByNameAscSlugAsc(UUID createdById);

    Optional<Organization> findByIdAndDeletedAtIsNull(UUID id);
}
