package com.strangequark.trasck.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventConsumerConfigRepository extends JpaRepository<EventConsumerConfig, UUID> {
    List<EventConsumerConfig> findByEnabledTrue();

    Optional<EventConsumerConfig> findByConsumerKey(String consumerKey);
}
