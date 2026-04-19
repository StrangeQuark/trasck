package com.strangequark.trasck.activity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLogRepository extends JpaRepository<WorkLog, UUID> {
    List<WorkLog> findByWorkItemIdAndDeletedAtIsNullOrderByWorkDateDescCreatedAtDesc(UUID workItemId);

    Optional<WorkLog> findByIdAndWorkItemIdAndDeletedAtIsNull(UUID id, UUID workItemId);
}
