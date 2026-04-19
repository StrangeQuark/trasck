package com.strangequark.trasck.audit;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRetentionPolicyRepository extends JpaRepository<AuditRetentionPolicy, UUID> {
    Optional<AuditRetentionPolicy> findByWorkspaceId(UUID workspaceId);

    List<AuditRetentionPolicy> findByRetentionEnabledTrue();
}
