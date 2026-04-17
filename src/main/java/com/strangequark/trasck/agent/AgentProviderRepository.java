package com.strangequark.trasck.agent;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProviderRepository extends JpaRepository<AgentProvider, UUID> {
}
