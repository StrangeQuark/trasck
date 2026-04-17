package com.strangequark.trasck.integration;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIntegrationRepository extends JpaRepository<ExternalIntegration, UUID> {
}
