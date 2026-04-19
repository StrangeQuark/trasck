package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRepository extends JpaRepository<Screen, UUID> {
    List<Screen> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);
}
