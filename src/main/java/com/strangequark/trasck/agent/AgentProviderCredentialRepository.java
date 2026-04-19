package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProviderCredentialRepository extends JpaRepository<AgentProviderCredential, UUID> {
    List<AgentProviderCredential> findByProviderIdAndCredentialTypeAndActiveTrue(UUID providerId, String credentialType);
}
