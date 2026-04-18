package com.strangequark.trasck.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatcherRepository extends JpaRepository<Watcher, WatcherId> {
    List<Watcher> findByIdWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
}
