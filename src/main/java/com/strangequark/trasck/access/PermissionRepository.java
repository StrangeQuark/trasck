package com.strangequark.trasck.access;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByOrderByCategoryAscKeyAsc();

    List<Permission> findByKeyIn(Collection<String> keys);

    Optional<Permission> findByKey(String key);
}
