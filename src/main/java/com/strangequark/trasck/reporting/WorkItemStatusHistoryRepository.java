package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemStatusHistoryRepository extends JpaRepository<WorkItemStatusHistory, UUID> {
    List<WorkItemStatusHistory> findByWorkItemIdOrderByChangedAtAsc(UUID workItemId);
}
