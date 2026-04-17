package com.strangequark.trasck.agent;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskRepositoryLinkRepository extends JpaRepository<AgentTaskRepositoryLink, UUID> {
}
