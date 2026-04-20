package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByWebhookIdOrderByCreatedAtDesc(UUID webhookId);

    @Query(value = """
            select wd.*
            from webhook_deliveries wd
            join webhooks w on w.id = wd.webhook_id
            where wd.id = :deliveryId
              and w.workspace_id = :workspaceId
            """, nativeQuery = true)
    java.util.Optional<WebhookDelivery> findByIdAndWorkspaceId(
            @Param("deliveryId") UUID deliveryId,
            @Param("workspaceId") UUID workspaceId
    );

    @Query(value = """
            select wd.*
            from webhook_deliveries wd
            join webhooks w on w.id = wd.webhook_id
            where w.workspace_id = :workspaceId
              and w.enabled = true
              and (
                  wd.status = 'queued'
                  or (wd.status = 'failed' and wd.next_retry_at is not null and wd.next_retry_at <= :now)
              )
            order by wd.created_at asc
            limit :limit
            """, nativeQuery = true)
    List<WebhookDelivery> findProcessableDeliveries(
            @Param("workspaceId") UUID workspaceId,
            @Param("now") OffsetDateTime now,
            @Param("limit") int limit
    );
}
