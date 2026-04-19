package com.strangequark.trasck.planning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IterationWorkItemRepository extends JpaRepository<IterationWorkItem, IterationWorkItemId> {
    Optional<IterationWorkItem> findByIdIterationIdAndIdWorkItemId(UUID iterationId, UUID workItemId);

    List<IterationWorkItem> findByIdIterationIdOrderByAddedAtAsc(UUID iterationId);

    List<IterationWorkItem> findByIdIterationIdAndRemovedAtIsNullOrderByAddedAtAsc(UUID iterationId);
}
