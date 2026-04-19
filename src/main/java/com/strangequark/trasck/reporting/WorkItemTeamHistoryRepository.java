package com.strangequark.trasck.reporting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemTeamHistoryRepository extends JpaRepository<WorkItemTeamHistory, UUID> {
    List<WorkItemTeamHistory> findByWorkItemIdOrderByChangedAtAsc(UUID workItemId);
}
