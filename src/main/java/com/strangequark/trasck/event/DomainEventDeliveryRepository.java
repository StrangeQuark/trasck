package com.strangequark.trasck.event;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainEventDeliveryRepository extends JpaRepository<DomainEventDelivery, UUID> {
    List<DomainEventDelivery> findByDomainEventId(UUID domainEventId);

    Optional<DomainEventDelivery> findByDomainEventIdAndConsumerKey(UUID domainEventId, String consumerKey);

    List<DomainEventDelivery> findByDomainEventIdAndDeliveryStatusIn(UUID domainEventId, Collection<String> deliveryStatuses);

    boolean existsByDomainEventIdAndDeliveryStatus(UUID domainEventId, String deliveryStatus);

    long countByDomainEventIdAndDeliveryStatusNot(UUID domainEventId, String deliveryStatus);
}
