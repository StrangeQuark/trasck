package com.strangequark.trasck.agent;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProviderCredentialRepository extends JpaRepository<AgentProviderCredential, UUID> {
}
