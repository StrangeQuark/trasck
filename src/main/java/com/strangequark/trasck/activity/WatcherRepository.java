package com.strangequark.trasck.activity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatcherRepository extends JpaRepository<Watcher, WatcherId> {
}
