package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemEstimateHistoryRepository extends JpaRepository<WorkItemEstimateHistory, UUID> {
    List<WorkItemEstimateHistory> findByWorkItemIdOrderByChangedAtAsc(UUID workItemId);
}
