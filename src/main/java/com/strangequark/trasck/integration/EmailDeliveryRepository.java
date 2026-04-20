package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {
    List<EmailDelivery> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    @Query(value = """
            select *
            from email_deliveries
            where workspace_id = :workspaceId
              and (
                  status = 'queued'
                  or (status = 'failed' and next_retry_at is not null and next_retry_at <= :now)
              )
            order by created_at asc
            limit :limit
            """, nativeQuery = true)
    List<EmailDelivery> findProcessableDeliveries(
            @Param("workspaceId") UUID workspaceId,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );
}
