package com.strangequark.trasck.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {
    List<DomainEvent> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);
}
