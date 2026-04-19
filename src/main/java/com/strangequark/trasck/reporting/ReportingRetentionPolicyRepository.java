package com.strangequark.trasck.reporting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportingRetentionPolicyRepository extends JpaRepository<ReportingRetentionPolicy, UUID> {
    Optional<ReportingRetentionPolicy> findByWorkspaceId(UUID workspaceId);
}
