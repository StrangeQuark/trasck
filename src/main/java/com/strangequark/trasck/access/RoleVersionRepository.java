package com.strangequark.trasck.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleVersionRepository extends JpaRepository<RoleVersion, UUID> {
    List<RoleVersion> findByRoleIdOrderByVersionNumberDesc(UUID roleId);

    Optional<RoleVersion> findByIdAndRoleId(UUID id, UUID roleId);

    long countByRoleId(UUID roleId);

    @Query("""
            select coalesce(max(version.versionNumber), 0)
            from RoleVersion version
            where version.roleId = :roleId
            """)
    int maxVersionNumber(@Param("roleId") UUID roleId);
}
