package com.strangequark.trasck.search;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {
}
