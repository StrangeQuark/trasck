package com.strangequark.trasck.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentProviderCredentialRepository extends JpaRepository<AgentProviderCredential, UUID> {
    List<AgentProviderCredential> findByProviderIdAndCredentialTypeAndActiveTrue(UUID providerId, String credentialType);

    List<AgentProviderCredential> findByProviderIdOrderByCreatedAtAsc(UUID providerId);

    Optional<AgentProviderCredential> findByIdAndProviderId(UUID id, UUID providerId);
}
