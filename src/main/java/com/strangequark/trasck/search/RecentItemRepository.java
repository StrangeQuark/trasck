package com.strangequark.trasck.search;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentItemRepository extends JpaRepository<RecentItem, UUID> {
}
