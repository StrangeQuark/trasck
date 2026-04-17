package com.strangequark.trasck.workitem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemClosureRepository extends JpaRepository<WorkItemClosure, WorkItemClosureId> {
}
