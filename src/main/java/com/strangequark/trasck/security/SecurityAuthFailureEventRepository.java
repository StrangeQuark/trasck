package com.strangequark.trasck.security;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuthFailureEventRepository extends JpaRepository<SecurityAuthFailureEvent, UUID> {
    long countByRealm(String realm);
}
