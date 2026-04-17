package com.strangequark.trasck.activity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkLogRepository extends JpaRepository<WorkLog, UUID> {
}
