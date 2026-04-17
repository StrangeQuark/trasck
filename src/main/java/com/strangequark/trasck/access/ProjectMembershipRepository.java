package com.strangequark.trasck.access;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
}
