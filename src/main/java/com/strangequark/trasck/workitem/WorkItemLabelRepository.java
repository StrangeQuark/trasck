package com.strangequark.trasck.workitem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemLabelRepository extends JpaRepository<WorkItemLabel, WorkItemLabelId> {
}
