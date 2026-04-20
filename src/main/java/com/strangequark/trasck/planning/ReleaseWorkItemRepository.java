package com.strangequark.trasck.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseWorkItemRepository extends JpaRepository<ReleaseWorkItem, ReleaseWorkItemId> {
    List<ReleaseWorkItem> findByIdReleaseIdOrderByAddedAtAsc(UUID releaseId);

    Optional<ReleaseWorkItem> findByIdReleaseIdAndIdWorkItemId(UUID releaseId, UUID workItemId);
}
