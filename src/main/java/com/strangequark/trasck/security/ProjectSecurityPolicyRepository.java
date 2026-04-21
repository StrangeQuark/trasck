package com.strangequark.trasck.security;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSecurityPolicyRepository extends JpaRepository<ProjectSecurityPolicy, UUID> {
}
