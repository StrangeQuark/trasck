package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemAssignmentHistoryRepository extends JpaRepository<WorkItemAssignmentHistory, UUID> {
    List<WorkItemAssignmentHistory> findByWorkItemIdOrderByChangedAtAsc(UUID workItemId);
}
