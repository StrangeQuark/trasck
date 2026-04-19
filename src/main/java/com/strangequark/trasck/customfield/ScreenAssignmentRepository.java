package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenAssignmentRepository extends JpaRepository<ScreenAssignment, UUID> {
    List<ScreenAssignment> findByScreenIdOrderByPriorityAsc(UUID screenId);

    Optional<ScreenAssignment> findByIdAndScreenId(UUID id, UUID screenId);
}
