package com.strangequark.trasck.access;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    @Query("""
            select count(rp) > 0
            from RolePermission rp
            join Permission p on p.id = rp.id.permissionId
            where rp.id.roleId = :roleId and p.key = :permissionKey
            """)
    boolean roleHasPermission(@Param("roleId") UUID roleId, @Param("permissionKey") String permissionKey);

    List<RolePermission> findByIdRoleId(UUID roleId);

    void deleteByIdRoleId(UUID roleId);

    @Query("""
            select p.key
            from RolePermission rp
            join Permission p on p.id = rp.id.permissionId
            where rp.id.roleId = :roleId
            order by p.key asc
            """)
    List<String> findPermissionKeysByRoleId(@Param("roleId") UUID roleId);
}
