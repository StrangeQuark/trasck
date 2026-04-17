package com.strangequark.trasck.workitem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemComponentRepository extends JpaRepository<WorkItemComponent, WorkItemComponentId> {
}
