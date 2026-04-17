package com.strangequark.trasck.workitem;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityRepository extends JpaRepository<Priority, UUID> {
}
